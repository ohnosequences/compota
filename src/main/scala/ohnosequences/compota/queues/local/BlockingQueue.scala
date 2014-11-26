package ohnosequences.compota.queues.local

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.queues.{QueueAux, Queue}
import ohnosequences.compota.serialization.Serializer

import scala.util.{Try, Success}


class BlockingQueue[T](size: Int) extends Queue[T] {

  val rawQueue = new ArrayBlockingQueue[T](size)

  case class SimpleMessage[EE](body: EE) extends QueueMessage[EE] {
    override def getBody = Success(body)
  }

  override type Message[EEE] = SimpleMessage[EEE]

  override def deleteMessage(message: SimpleMessage[T]) = Success(())


  class BlockingQueueReader extends QueueReader {
    override def getMessage = Success(SimpleMessage(rawQueue.take()))
  }

  type QR = BlockingQueueReader

  override def getReader = Success(new BlockingQueueReader)

  class BlockingQueueWriter extends QueueWriter {
    override def write(values: List[T]) = Try {
      values.foreach(rawQueue.put)
    }
  }

  type QW = BlockingQueueWriter


  override def getWriter = Success(new BlockingQueueWriter)
}