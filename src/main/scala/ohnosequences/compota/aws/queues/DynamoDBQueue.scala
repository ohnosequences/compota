package ohnosequences.compota.aws.queues

import com.amazonaws.AmazonClientException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.sqs.model._
import ohnosequences.awstools.dynamodb.Utils
import ohnosequences.compota.aws.deployment.Metadata
import ohnosequences.logging.{Logger, ConsoleLogger}

import scala.collection.JavaConversions._

import com.amazonaws.services.dynamodbv2.model._
import ohnosequences.compota.aws.{Resources, SQSUtils, AWSClients}
import ohnosequences.compota.queues._
import ohnosequences.compota.serialization.Serializer

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class SQSMessage[E](val id: String, val body: E, val handle: String) extends QueueMessage[E] {
  def getBody: Try[E] = Success(body)

  def getId: Try[String] = Success(id)
}

class RawItem(val id: String, val value: String) {
  def makeDBWriteRequest = {
    new WriteRequest(new PutRequest(Map[String, AttributeValue](
      DynamoDBQueue.idAttr -> new AttributeValue().withS(id),
      DynamoDBQueue.valueAttr -> new AttributeValue().withS(value)
    )))
  }

  def makeSQSEntry: SendMessageBatchRequestEntry = {
    new SendMessageBatchRequestEntry(id, value).withMessageAttributes(Map[String, MessageAttributeValue](
      DynamoDBQueue.idAttr -> new MessageAttributeValue().withStringValue(id).withDataType("String")
    ))
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
      Utils.writeWriteRequests(queueOp.aws.ddb, queueOp.tableName, items.map(_.makeDBWriteRequest), logger).flatMap { r =>
        SQSUtils.writeBatch(queueOp.aws.sqs.sqs, queueOp.sqsUrl, items.map { item => new RawItem(item.id, "")}) //no body
      }
    }
  }
}


class DynamoDBQueueReader[T](queueOp: DynamoDBQueueOP[T]) extends QueueReader[T, SQSMessage[T]] {


  object TailRecCanNotBeClassMembers {

    @tailrec
    def getMessageFromSQSMessage(ddb: AmazonDynamoDB,
                                 tableName: String,
                                 serializer: Serializer[T],
                                 message: com.amazonaws.services.sqs.model.Message): Try[SQSMessage[T]] = {


      val id = message.getAttributes.get(DynamoDBQueue.idAttr)
      //failure repeat noitem item
      val itemValue: Try[Option[Either[Unit, String]]] = try {
        ddb.getItem(new GetItemRequest()
          .withTableName(tableName)
          .withKey(Map(DynamoDBQueue.idAttr -> new AttributeValue().withS(id)))
          .withAttributesToGet(List(DynamoDBQueue.idAttr, DynamoDBQueue.valueAttr))
        ).getItem match {
          case null => Success(Some(Left(())))
          case itemMap => {
            Success(Some(Right(itemMap.get(DynamoDBQueue.valueAttr).getS)))
          }
        }
      } catch {
        case p: ProvisionedThroughputExceededException => {
          //repeat
          Success(None)
        }
        case a: AmazonClientException => {
          Failure(a)
        }
      }

      itemValue match {
        case Success(Some(Left(()))) => {
          //no such item
          Failure(new Error("no such item"))
        }
        case Success(Some(Right(value))) => {
          serializer.fromString(value).map { body =>
            new SQSMessage(id, body, message.getReceiptHandle)
          }
        }
        case Failure(f) => Failure(f)
        case Success(None) => {
          //repeat
          getMessageFromSQSMessage(ddb, tableName, serializer, message)
        }
      }

    }
  }

  import TailRecCanNotBeClassMembers._


  override def receiveMessage: Try[SQSMessage[T]] = {
    SQSUtils.receiveMessage(queueOp.aws.sqs.sqs, queueOp.sqsUrl).flatMap { sqsMessage =>
      getMessageFromSQSMessage(queueOp.aws.ddb, queueOp.tableName, queueOp.serializer, sqsMessage)
    }
  }
}

class DynamoDBContext (
  val aws: AWSClients,
  val metadata: Metadata,
  val logger: Logger
)

class DynamoDBQueueOP[T](val tableName: String, val sqsUrl: String, val aws: AWSClients, val serializer: Serializer[T])
  extends QueueOps[T, SQSMessage[T], DynamoDBQueueReader[T], DynamoDBQueueWriter[T]] {
  override def deleteMessage(message: SQSMessage[T]): Try[Unit] = ???

  override def delete(): Try[Unit] = ???

  override def writer: Try[DynamoDBQueueWriter[T]] = Success(new DynamoDBQueueWriter[T](DynamoDBQueueOP.this, serializer))

  override def isEmpty: Boolean = ???

  override def reader: Try[DynamoDBQueueReader[T]] = Success(new DynamoDBQueueReader[T](DynamoDBQueueOP.this))
}

object DynamoDBQueue {
  val idAttr = "id"
  val valueAttr = "val"

  val hash = new AttributeDefinition(idAttr, ScalarAttributeType.S)
  // val range: AttributeDefinition =  new AttributeDefinition(valueAttr, ScalarAttributeType.S)

}

class DynamoDBQueue[T](name: String, val serializer: Serializer[T]) extends Queue[T, DynamoDBContext](name) {

  def receiveMessageWaitTimeSeconds = 20

  override type Msg = SQSMessage[T]

  override def create(ctx: DynamoDBContext): Try[QueueOps[T, SQSMessage[T], DynamoDBQueueReader[T], DynamoDBQueueWriter[T]]] = {
    Try {
      Utils.createTable(
        ddb = ctx.aws.ddb,
        tableName = Resources.dynamodbTable(ctx.metadata, name),
        hash = DynamoDBQueue.hash,
        range = None,
        logger = ctx.logger
      )

      val queueUrl = ctx.aws.sqs.sqs.createQueue(new CreateQueueRequest()
        .withQueueName(Resources.dynamodbTable(ctx.metadata, name))
        .withAttributes(Map(QueueAttributeName.ReceiveMessageWaitTimeSeconds.toString -> receiveMessageWaitTimeSeconds.toString)) //max
      ).getQueueUrl

      new DynamoDBQueueOP[Elmnt](name, queueUrl, ctx.aws, serializer)
    }
  }


  override type Writer = DynamoDBQueueWriter[T]
  override type Reader = DynamoDBQueueReader[T]
}

