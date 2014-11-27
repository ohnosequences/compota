package ohnosequences.compota.queues.local

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.queues._

import scala.util.{Try, Success}


case class SimpleMessage[E](id: String, body: E) extends QueueMessage[E] {
  override def getBody = Success(body)

  override def getId: Try[String] = Success(id)
}

class BlockingQueueReader[T](queue: BlockingQueue[T]) extends QueueReader[T, SimpleMessage[T]] {
  override def getMessage = Try {
    val (id, body) = queue.rawQueue.take()
    SimpleMessage(id, body)
  }
}

class BlockingQueueWriter[T](queue: BlockingQueue[T]) extends QueueWriter[T, SimpleMessage[T]] {
  override def writeRaw(values: List[(String, T)]) = Try {

    values.foreach(queue.rawQueue.put)
  }
}

class BlockingQueue[T](name: String, size: Int) extends Queue[T](name) {

  val rawQueue = new ArrayBlockingQueue[(String, T)](size)

  override type Message = SimpleMessage[T]

  override def deleteMessage(message: SimpleMessage[T]) = Success(())

  type QR = BlockingQueueReader[T]

  override def getReader = Success(new BlockingQueueReader(BlockingQueue.this))


  type QW = BlockingQueueWriter[T]

  override def getWriter = Success(new BlockingQueueWriter(BlockingQueue.this))

  override def isEmpty: Boolean = rawQueue.isEmpty
}
