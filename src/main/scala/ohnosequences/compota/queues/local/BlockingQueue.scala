package ohnosequences.compota.queues.local

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.queues.{QueueAux, Queue}
import ohnosequences.compota.serialization.Serializer

import scala.util.{Try, Success}


class BlockingQueue[T](size: Int) extends Queue[T] {

  val rawQueue = new ArrayBlockingQueue[(String, T)](size)

  case class SimpleMessage[EE](id: String, body: EE) extends QueueMessage[EE] {
    override def getBody = Success(body)

    override def getId: Try[String] = Success(id)
  }

  override type Message[EEE] = SimpleMessage[EEE]

  override def deleteMessage(message: SimpleMessage[T]) = Success(())


  class BlockingQueueReader extends QueueReader {
    override def getMessage = Try {
      val (id, body) = rawQueue.take()
      SimpleMessage(id, body)
    }
  }

  type QR = BlockingQueueReader

  override def getReader = Success(new BlockingQueueReader)

  class BlockingQueueWriter extends QueueWriter {
    override def write(values: List[(String, T)]) = Try {

      values.foreach(rawQueue.put)
    }
  }

  type QW = BlockingQueueWriter


  override def getWriter = Success(new BlockingQueueWriter)
}
