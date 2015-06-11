package ohnosequences.compota.aws.queues


import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model._
import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.dynamodb.DynamoDBUtils
import ohnosequences.awstools.utils.{SQSQueueInfo, SQSUtils}
import ohnosequences.benchmark.Bench
import ohnosequences.compota.aws.deployment.{AnyMetadata, Metadata}
import ohnosequences.logging.{Logger, ConsoleLogger}

import scala.collection.JavaConversions._

import com.amazonaws.services.dynamodbv2.model._
import ohnosequences.compota.aws.Resources
import ohnosequences.compota.queues._
import ohnosequences.compota.serialization.Serializer


import scala.concurrent.duration.Duration
import scala.concurrent.duration._

import scala.util.{Failure, Success, Try}


class RawItem(val id: String, val value: String) {
  def makeDBWriteRequest = {
    new WriteRequest(new PutRequest(Map[String, AttributeValue](
      DynamoDBQueue.idAttr -> new AttributeValue().withS(id),
      DynamoDBQueue.valueAttr -> new AttributeValue().withS(value)
    )))
  }
}

class DynamoDBQueueWriter[T](queueOp: DynamoDBQueueOP[T], serializer: Serializer[T]) extends QueueWriter[T] {

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
  override def getBody: Try[Option[T]] = {
    val itemWrap = queueOp.logger.benchExecute("DynamoDB read", bench) {
      DynamoDBUtils.getItem(
        ddb = queueOp.aws.ddb,
        logger = Some(queueOp.logger),
        tableName = queueOp.tableName,
        key = Map(DynamoDBQueue.idAttr -> new AttributeValue().withS(id)),
        attributesToGet = List(DynamoDBQueue.idAttr, DynamoDBQueue.valueAttr)
      )
    }

    itemWrap.flatMap {
      case None => Success(None)
      case Some(item) =>
        item.get(DynamoDBQueue.valueAttr) match {
          case None => Failure(new Error(DynamoDBQueue.valueAttr + " is empty"))
          case Some(s) => {
            queueOp.serializer.fromString(s.getS).map(Some(_))
          }
        }
    }
  }

  override val id: String = sqsMessage.getBody
}

class DynamoDBQueueReader[T](val queueOp: DynamoDBQueueOP[T]) extends QueueReader[T, DynamoDBMessage[T]] {

  override def receiveMessage(logger: Logger): Try[Option[DynamoDBMessage[T]]] = {
    Try {
      val sqs = queueOp.aws.sqs
      val res = sqs.sqs.receiveMessage(new ReceiveMessageRequest()
        .withQueueUrl(queueOp.sqsUrl)
        .withWaitTimeSeconds(queueOp.queue.receiveMessageWaitTime.toSeconds.toInt) //max
        .withMaxNumberOfMessages(1)
      )
      res.getMessages.headOption.map { sqsMessage =>
        new DynamoDBMessage(sqsMessage, queueOp, queueOp.bench)
      }
    }
  }
}

case class DynamoDBContext (
  aws: AWSClients,
  metadata: AnyMetadata,
  logger: Logger
)



class DynamoDBQueueOP[T](val queue: DynamoDBQueue[T], val tableName: String, val sqsUrl: String, val aws: AWSClients, val serializer: Serializer[T], val bench: Option[Bench])
  extends QueueOp[T, DynamoDBMessage[T], DynamoDBQueueReader[T], DynamoDBQueueWriter[T]] {

  val logger = new ConsoleLogger("DynamoDB OP")

  //this operation is not atomic
  override def deleteMessage(message: DynamoDBMessage[T]): Try[Unit] = {

    logger.benchExecute("DynamoDB delete", bench){DynamoDBUtils.deleteItem(
      aws.ddb,
      Some(logger),
      tableName,
      Map(DynamoDBQueue.idAttr -> new AttributeValue().withS(message.id))
    )}

    logger.benchExecute("SQS delete", bench) {
      SQSUtils.deleteMessage(aws.sqs.sqs, sqsUrl, message.sqsMessage.getReceiptHandle)
    }
  }

  override def size: Try[Int] = Success(0)

  override def delete(): Try[Unit] = {
    DynamoDBUtils.deleteTable(aws.ddb, tableName).flatMap { res =>
      SQSUtils.deleteQueue(aws.sqs.sqs, sqsUrl)
    }
  }

  def sqsInfo(): Try[SQSQueueInfo] = {
    SQSUtils.getSQSInfo(aws.sqs.sqs, sqsUrl)
  }

  override def writer: Try[DynamoDBQueueWriter[T]] = Success(new DynamoDBQueueWriter[T](DynamoDBQueueOP.this, serializer))

  override def isEmpty: Try[Boolean] = {
    DynamoDBUtils.isEmpty(aws.ddb, tableName, Some(logger))
  }

  override def get(key: String): Try[T] = {

    DynamoDBUtils.getItem(
      ddb = aws.ddb,
      logger = Some(logger),
      tableName = tableName,
      Map(DynamoDBQueue.idAttr -> new AttributeValue().withS(key)),
      attributesToGet = Seq(DynamoDBQueue.valueAttr)
    ).flatMap {
      case None => Failure(new Error("item with key " + key + " doesn't exist in " + tableName))
      case Some(item )=> serializer.fromString(item(DynamoDBQueue.valueAttr).getS)
    }
  }


  override def list(lastKey: Option[String], limit: Option[Int]): Try[(Option[String], List[String])] = {
    DynamoDBUtils.list(
      ddb = aws.ddb,
      logger = Some(logger),
      tableName = tableName,
      lastKey = lastKey.map { v => Map(DynamoDBQueue.idAttr -> new AttributeValue().withS(v)) },
      attributesToGet = Seq(DynamoDBQueue.idAttr),
      limit = limit
    ).map { res =>
      (res._1.flatMap { i => i.get(DynamoDBQueue.idAttr).map(_.getS)},
      res._2.map { i => i(DynamoDBQueue.idAttr).getS})
    }

  }

  override def reader: Try[DynamoDBQueueReader[T]] = Success(new DynamoDBQueueReader[T](DynamoDBQueueOP.this))

  //todo improve performance
 // override def forEach[T](f: (String, T) => T): Try[Unit] = super.forEach(f)
}

object DynamoDBQueue {
  val idAttr = "id"
  val valueAttr = "val"

  val hash = new AttributeDefinition(idAttr, ScalarAttributeType.S)
}

class DynamoDBQueue[T](name: String,
                       val serializer: Serializer[T],
                       bench: Option[Bench] = None,
                       val receiveMessageWaitTime: Duration = Duration(20, SECONDS), //max 20 seconds
                       val visibilityTimeout: Duration = Duration(10, MINUTES),
                       readThroughput: Long = 1,
                       writeThroughput: Long = 1
                       ) extends Queue[T, DynamoDBContext](name) { queue =>

  override type QueueQueueMessage = DynamoDBMessage[T]
  override type QueueQueueWriter = DynamoDBQueueWriter[T]
  override type QueueQueueReader = DynamoDBQueueReader[T]
  override type QueueQueueOp = DynamoDBQueueOP[T]


  override def create(ctx: DynamoDBContext): Try[QueueQueueOp] = {
    Try {
      DynamoDBUtils.createTable(
        ddb = ctx.aws.ddb,
        tableName = Resources.dynamodbTable(ctx.metadata, name),
        hash = DynamoDBQueue.hash,
        range = None,
        logger = ctx.logger,
        writeThroughput = writeThroughput,
        readThroughput = readThroughput
      )

      val queueUrl = ctx.aws.sqs.sqs.createQueue(new CreateQueueRequest()
        .withQueueName(Resources.sqsQueue(ctx.metadata, name))
        .withAttributes(
          Map(
            QueueAttributeName.ReceiveMessageWaitTimeSeconds.toString -> receiveMessageWaitTime.toSeconds.toString,
            QueueAttributeName.VisibilityTimeout.toString -> visibilityTimeout.toSeconds.toString
          )
        ) //max
      ).getQueueUrl

      new DynamoDBQueueOP[T](queue, Resources.dynamodbTable(ctx.metadata, name), queueUrl, ctx.aws, serializer, bench)
    }
  }



}

