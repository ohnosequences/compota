package ohnosequences.compota.aws

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{Message, ReceiveMessageRequest, SendMessageBatchRequest}
import ohnosequences.compota.aws.queues.{DynamoDBQueue, RawItem}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConversions._


object SQSUtils {

  @tailrec
  def receiveMessage(sqs: AmazonSQS, queueUrl: String): Try[Message] = {
    Try {
      val res = sqs.receiveMessage(new ReceiveMessageRequest()
        .withQueueUrl(queueUrl)
        .withWaitTimeSeconds(20) //max
        .withMaxNumberOfMessages(1)
       // .withAttributeNames(DynamoDBQueue.idAttr)
      )
      res.getMessages.headOption
    } match {
      case Failure(f) => Failure[Message](f)
      case Success(None) => {
        receiveMessage(sqs, queueUrl)
      }
      case Success(Some(message)) => Success(message)
    }
  }

  @tailrec
  def writeBatch(sqs: AmazonSQS, queueUrl: String, items: List[RawItem]): Try[Unit] = {

    if (items == null | items.isEmpty) {
      Success(())
    } else {
      val (left, right) = items.splitAt(10)
      Try {
        sqs.sendMessageBatch(new SendMessageBatchRequest()
          .withQueueUrl(queueUrl)
          .withEntries(left.zipWithIndex.map { case (item, i) =>
            item.makeSQSEntry(i + 1)
        })
        )
        ()
      } match {
        case Failure(f) => Failure(f)
        case Success(_) => {
          writeBatch(sqs, queueUrl, right)
        }
      }
    }
  }
}
