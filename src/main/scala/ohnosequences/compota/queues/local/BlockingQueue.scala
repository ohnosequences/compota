package ohnosequences.compota.queues.local

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.queues._

import scala.util.{Try, Success}


case class SimpleMessage[E](id: String, body: E) extends QueueMessage[E] {
  override def getBody = Success(body)

  override def getId: Try[String] = Success(id)
}

class BlockingQueueReader[T](queue: BlockingQueueOp[T]) extends QueueReader[T, SimpleMessage[T]] {
  override def getMessage = Try {
    val (id, body) = queue.rawQueue.take()
    SimpleMessage(id, body)
  }
}

class BlockingQueueWriter[T](queue: BlockingQueueOp[T]) extends QueueWriter[T, SimpleMessage[T]] {
  override def writeRaw(values: List[(String, T)]) = Try {

    values.foreach(queue.rawQueue.put)
  }
}

class BlockingQueueOp[T](size: Int) extends QueueOp[T, SimpleMessage[T], BlockingQueueReader[T], BlockingQueueWriter[T]] {

  override def deleteMessage(message: SimpleMessage[T]) = Success(())


  override def getReader = Success(new BlockingQueueReader(BlockingQueueOp.this))

  override def getWriter = Success(new BlockingQueueWriter(BlockingQueueOp.this))

  override def isEmpty: Boolean = rawQueue.isEmpty

  override def delete() = Success(())


  val rawQueue = new ArrayBlockingQueue[(String, T)](size)
}

class BlockingQueue[T](name: String, size: Int) extends Queue[T, Unit](name) {

  override type Message = SimpleMessage[T]

  override type QR = BlockingQueueReader[T]
  override type QW = BlockingQueueWriter[T]

  override def create(ctx: Context): QueueOp[Element, Message, QR, QW] = new BlockingQueueOp[Element](size)
}
