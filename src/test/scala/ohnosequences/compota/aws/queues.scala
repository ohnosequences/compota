package ohnosequences.compota.aws

import ohnosequences.compota.aws.deployment.Metadata
import ohnosequences.compota.aws.queues._
import ohnosequences.compota.serialization.intSerializer
import ohnosequences.logging.{ConsoleLogger, Logger}
import org.junit.Test
import org.junit.Assert._

import scala.annotation.tailrec
import scala.util.{Failure, Try, Success}


class QueueTest {

  val logger = new ConsoleLogger("queue-test")

  val timeout: Long = 200*1000


  def checkTry[T](atry: Try[T]): Unit = {
    atry match {
      case Failure(f) => f.printStackTrace()
      case Success(t) => ()
    }
    assertEquals(true, atry.isSuccess)
  }

  //@Test
  def writeAndRead() {
    TestCredentials.aws.foreach { aws =>
      val queue = new DynamoDBQueue("test", intSerializer)
      val context = new DynamoDBContext(
        metadata = new Metadata {
          override val artifact: String = "test"
          override val jarUrl: String = ""
        },
        logger = logger,
        aws = aws
      )

      checkTry(queue.create(context).flatMap { queueOp =>
        queueOp.reader.flatMap{ reader =>
          queueOp.writer.flatMap { writer =>
            testQueue(reader, writer)
          }
        }
      })

    }
  }

  def testQueue(reader: DynamoDBQueueReader[Int], writer: DynamoDBQueueWriter[Int]): Try[Unit] = {
    @tailrec
    def readAllMessages(idsToRead: Set[Int], readIds: Set[String], started: Long): Try[Unit] = {
      logger.info(idsToRead.size + " messages left")
      if(System.currentTimeMillis() - started > timeout) {
        Failure(new Error("timeout"))
      } else if (idsToRead.isEmpty) {
        Success(())
      } else {
        reader.receiveMessage.flatMap { message =>
          message.getBody match {
            case Failure(t) => {
              if (readIds.contains(message.id)) {
                logger.warn(t)
                Success(None)
              } else {
                Failure(t)
              }
            }
            case Success(value) => {
              reader.queueOp.deleteMessage(message)
              Success(Some((message.id, value)))
            }
          }
        } match {
          case Failure(f) => Failure(f)
          case Success(None) =>  readAllMessages(idsToRead, readIds, started)
          case Success(Some((id, value))) => readAllMessages(idsToRead.-(value), readIds + id, started)
        }

      }
    }

    val items = (1 to 100).toList
    logger.info("writing messages")
    checkTry( writer.writeMessages("i", items))

    logger.info("receiving messages")
    readAllMessages(items.toSet, Set[String](), System.currentTimeMillis())
  }




}
