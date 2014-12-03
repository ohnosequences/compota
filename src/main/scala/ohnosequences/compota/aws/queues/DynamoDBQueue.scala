package ohnosequences.compota.aws.queues

import com.amazonaws.AmazonClientException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.sqs.model.{Message => SMessage, _}
import ohnosequences.awstools.dynamodb.Utils
import ohnosequences.compota.aws.deployment.Metadata
import ohnosequences.logging.{Logger, ConsoleLogger}

import scala.collection.JavaConversions._

import com.amazonaws.services.dynamodbv2.model._
import ohnosequences.compota.aws.{Resources, SQSUtils, AWSClients}
import ohnosequences.compota.queues.{Message => CompotaMessage, _}
import ohnosequences.compota.serialization.Serializer

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

trait AnySQSMessage extends CompotaMessage[SMessage]

case class SQSMessage(val id: String, val body: SMessage, val handle: String) extends AnySQSMessage { 

  def getBody: Try[Body] = Success(body)

  def getId: Try[String] = Success(id)
}

case class RawItem(val id: String, val value: String) {

  // TODO: return type
  def makeDBWriteRequest = {
    new WriteRequest(new PutRequest(Map[String, AttributeValue](
      DynamoDBQueue.idAttr -> new AttributeValue().withS(id),
      DynamoDBQueue.valueAttr -> new AttributeValue().withS(value)
    )))
  }

  def makeSQSEntry(i: Int): SendMessageBatchRequestEntry = {
    new SendMessageBatchRequestEntry(i.toString, value)

  //    .withMessageAttributes(Map[String, MessageAttributeValue](
//      DynamoDBQueue.idAttr -> new MessageAttributeValue().withStringValue(id).withDataType("String")
//    ))
  }

}

case class DynamoDBQueueWriter[Q <: AnyDynamoDBQueue](
  val context: DynamoDBContext,
  val queueOp: DynamoDBQueueOP[Q], 
  val serializer: Serializer[SMessage]
) 
extends QueueWriter[Q] {
  
  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]
  // TODO: types
  lazy val logger = context.logger
  lazy val ddb = context.aws.ddb
  lazy val tableName = queueOp.tableName


  def writeRaw(values: List[(String, SMessage)]): Try[Unit] = {
    Try {
      values.map {
        case (id, value) => new RawItem(id, serializer.toString(value).get)
      }
    }.flatMap { items: List[RawItem] =>
      //ddb
      Utils.writeWriteRequests(queueOp.aws.ddb, queueOp.tableName, items.map(_.makeDBWriteRequest), logger).flatMap { r =>
        logger.info("writing to SQS")
        SQSUtils.writeBatch(queueOp.aws.sqs.sqs, queueOp.sqsUrl, items.map { item => new RawItem(item.id, item.id)}) //no body
      }
    }
  }
}


case class DynamoDBQueueReader[Q <: AnyDynamoDBQueue](
  val context: DynamoDBContext,
  val queueOp: DynamoDBQueueOP[Q],
  val serializer: Serializer[SMessage]
) 
extends QueueReader[Q] {

  lazy val logger = context.logger

  object TailRecCanNotBeClassMembers {

    @tailrec
    def getMessageFromSQSMessage(ddb: AmazonDynamoDB,
                                 tableName: String,
                                 serializer: Serializer[SMessage],
                                 message: SMessage): Try[Queue#Message] = {


      val id = message.getBody
      logger.info(message.getAttributes.toMap.toString)
      //failure repeat noitem item
      val itemValue: Try[Option[Either[Unit, String]]] = try {
        //queueOp.
        logger.info("getting " + id)
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


  def receiveMessage: Try[Q#Message] = {
    SQSUtils.receiveMessage(queueOp.aws.sqs.sqs, queueOp.sqsUrl).flatMap { sqsMessage =>
      getMessageFromSQSMessage(queueOp.aws.ddb, queueOp.tableName, queueOp.serializer, sqsMessage)
    }
  }
}

case class DynamoDBContext (
  val aws: AWSClients,
  val metadata: Metadata,
  val logger: Logger
)

case class DynamoDBQueueOP[Q <: AnyDynamoDBQueue](
  val tableName: String,
  val sqsUrl: String,
  val context: DynamoDBContext,
  val serializer: Serializer[SMessage]
) 
extends QueueOps[Q] {

  type Reader = DynamoDBQueueReader[Q]
  type Writer = DynamoDBQueueWriter[Q]

  lazy val aws: AWSClients = context.aws

  def deleteMessage(message: SQSMessage): Try[Unit] = ???
  def delete(): Try[Unit] = ???
  def isEmpty: Boolean = ???

  def writer: Try[DynamoDBQueueWriter[Q]] = Success(
    DynamoDBQueueWriter[Q](context, DynamoDBQueueOP.this, serializer)
  )
  def reader: Try[DynamoDBQueueReader[Q]] = Success(
    DynamoDBQueueReader[Q](context, DynamoDBQueueOP.this, serializer)
  )
}

object DynamoDBQueue {
  val idAttr = "id"
  val valueAttr = "val"

  val hash = new AttributeDefinition(idAttr, ScalarAttributeType.S)
  // val range: AttributeDefinition =  new AttributeDefinition(valueAttr, ScalarAttributeType.S)

}

trait AnyDynamoDBQueue extends AnyQueue {

  type Message = SQSMessage
}

case class DynamoDBQueue(val qname: String, val serializer: Serializer[SMessage]) 
extends Queue[DynamoDBContext](qname) with AnyDynamoDBQueue with AnyQueueManager {

  type Queue = DynamoDBQueue
  type QueueOps = DynamoDBQueueOP[DynamoDBQueue]

  // TODO: WHAT??
  def receiveMessageWaitTimeSeconds = 20

  def create(ctx: DynamoDBContext): Try[QueueOps] = {
    Try {
      Utils.createTable(
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

      new DynamoDBQueueOP[DynamoDBQueue](
        tableName = Resources.dynamodbTable(ctx.metadata, name),
        sqsUrl = queueUrl, 
        context = ctx, 
        serializer
      )
    }
  }
}

