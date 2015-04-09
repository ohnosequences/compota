package ohnosequences.compota.aws.queues


import java.util.concurrent.atomic.AtomicBoolean

import com.amazonaws.services.sqs.model._
import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.dynamodb.DynamoDBUtils
import ohnosequences.awstools.utils.SQSUtils
import ohnosequences.benchmark.Bench
import ohnosequences.compota.aws.deployment.Metadata
import ohnosequences.logging.{Logger, ConsoleLogger}

import scala.collection.JavaConversions._

import com.amazonaws.services.dynamodbv2.model._
import ohnosequences.compota.aws.Resources
import ohnosequences.compota.queues._
import ohnosequences.compota.serialization.Serializer

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class SQSMessage[E](val id: String, val body: E, val handle: String) extends QueueMessage[E] {
  def getBody: Try[E] = Success(body)
}

class RawItem(val id: String, val value: String) {
  def makeDBWriteRequest = {
    new WriteRequest(new PutRequest(Map[String, AttributeValue](
      DynamoDBQueue.idAttr -> new AttributeValue().withS(id),
      DynamoDBQueue.valueAttr -> new AttributeValue().withS(value)
    )))
  }
}

class DynamoDBQueueWriter[T](queueOp: DynamoDBQueueOP[T], serializer: Serializer[T]) extends QueueWriter[T] {
  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]

  val logger = new ConsoleLogger("dynamodb writer")

  val ddb = queueOp.aws.ddb
  val tableName = queueOp.tableName


  override def writeRaw(values: List[(String, T)]): Try[Unit] = {
    Try {
      values.map {
        case (id, value) => new RawItem(id, serializer.toString(value).get)
      }
    }.flatMap { items: List[RawItem] =>
      //ddb

      logger.benchExecute("DynamoDB write", queueOp.bench) {
        DynamoDBUtils.writeWriteRequests(queueOp.aws.ddb, queueOp.tableName, items.map(_.makeDBWriteRequest), logger)
      }.flatMap { r =>

        val itemsS = items.map(_.id)

        logger.benchExecute("SQS write", queueOp.bench) {
          SQSUtils.writeBatch(queueOp.aws.sqs.sqs, queueOp.sqsUrl, itemsS)
        }
      }
    }
  }
}

class DynamoDBMessage[T](val sqsMessage: Message, queueOp: DynamoDBQueueOP[T], bench: Option[Bench]) extends QueueMessage[T] {
  override def getBody: Try[T] = {
    val itemWrap = queueOp.logger.benchExecute("DynamoDB read", bench) {
      DynamoDBUtils.getItem(
        queueOp.aws.ddb,
        queueOp.tableName,
        key = Map(DynamoDBQueue.idAttr -> new AttributeValue().withS(id)),
        attributesToGet = List(DynamoDBQueue.idAttr, DynamoDBQueue.valueAttr),
        queueOp.logger
      )
    }

    itemWrap.flatMap { item =>
      item.get(DynamoDBQueue.valueAttr) match {
        case None => Failure(new Error(DynamoDBQueue.valueAttr + " is empty"))
        case Some(s) => {
          queueOp.serializer.fromString(s.getS)
        }
      }
    }
  }

  override val id: String = sqsMessage.getBody
}

class DynamoDBQueueReader[T](val queueOp: DynamoDBQueueOP[T]) extends QueueReader[T, DynamoDBMessage[T]] {

 // val logger = new ConsoleLogger("DynamoDB reader")

  //todo add supperot for stop
  override def receiveMessage(logger: Logger, isStopped: => Boolean = {false}): Try[DynamoDBMessage[T]] = {
    val sqsMessageWrap: Try[Message] =
      logger.benchExecute("SQS read") {
        SQSUtils.receiveMessage(queueOp.aws.sqs.sqs, queueOp.sqsUrl)
      }

      sqsMessageWrap.map { sqsMessage => new DynamoDBMessage(sqsMessage, queueOp, queueOp.bench)}
    }
}

case class DynamoDBContext (
  aws: AWSClients,
  metadata: Metadata,
  logger: Logger
)

class DynamoDBQueueOP[T](val queue: DynamoDBQueue[T], val tableName: String, val sqsUrl: String, val aws: AWSClients, val serializer: Serializer[T], val bench: Option[Bench])
  extends QueueOp[T, DynamoDBMessage[T], DynamoDBQueueReader[T], DynamoDBQueueWriter[T]] {

  val logger = new ConsoleLogger("DynamoDB OP")


  override def deleteMessage(message: DynamoDBMessage[T]): Try[Unit] = {


    logger.benchExecute("DynamoDB delete", bench){DynamoDBUtils.deleteItem(
      aws.ddb,
      tableName,
      Map(DynamoDBQueue.idAttr -> new AttributeValue().withS(message.id)),
      logger
    )}

    //todo think about that!

    logger.benchExecute("SQS delete", bench) {
      SQSUtils.deleteMessage(aws.sqs.sqs, sqsUrl, message.sqsMessage.getReceiptHandle)
    }
  }

  //todo size


  override def size: Try[Int] = Success(0)

  override def delete(): Try[Unit] = ???

  override def writer: Try[DynamoDBQueueWriter[T]] = Success(new DynamoDBQueueWriter[T](DynamoDBQueueOP.this, serializer))

  override def isEmpty: Try[Boolean] = ???

  override def read(key: String): Try[T] = ???

  override def list(lastKey: Option[String], limit: Option[Int]): Try[(Option[String], List[String])] = ???

  override def reader: Try[DynamoDBQueueReader[T]] = Success(new DynamoDBQueueReader[T](DynamoDBQueueOP.this))


}

object DynamoDBQueue {
  val idAttr = "id"
  val valueAttr = "val"

  val hash = new AttributeDefinition(idAttr, ScalarAttributeType.S)
}

class DynamoDBQueue[T](name: String, val serializer: Serializer[T], bench: Option[Bench] = None) extends Queue[T, DynamoDBContext](name) { queue =>

  def receiveMessageWaitTimeSeconds = 20

  override type Msg = DynamoDBMessage[T]

  override def create(ctx: DynamoDBContext): Try[QueueOp[T, DynamoDBMessage[T], DynamoDBQueueReader[T], DynamoDBQueueWriter[T]]] = {
    Try {
      DynamoDBUtils.createTable(
        ddb = ctx.aws.ddb,
        tableName = Resources.dynamodbTable(ctx.metadata, name),
        hash = DynamoDBQueue.hash,
        range = None,
        logger = ctx.logger
      )

      val queueUrl = ctx.aws.sqs.sqs.createQueue(new CreateQueueRequest()
        .withQueueName(Resources.sqsQueue(ctx.metadata, name))
        .withAttributes(Map(QueueAttributeName.ReceiveMessageWaitTimeSeconds.toString -> receiveMessageWaitTimeSeconds.toString)) //max
      ).getQueueUrl

      new DynamoDBQueueOP[Elmnt](queue, Resources.dynamodbTable(ctx.metadata, name), queueUrl, ctx.aws, serializer, bench)
    }
  }


  override type Writer = DynamoDBQueueWriter[T]
  override type Reader = DynamoDBQueueReader[T]
}

