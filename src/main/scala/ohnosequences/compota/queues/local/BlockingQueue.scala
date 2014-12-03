package ohnosequences.compota.queues.local

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.queues._

import scala.util.{Try, Success}


case class SimpleMessage[E](id: String, body: E) extends Message[E] {
  
  def getBody: Try[E] = Success(body)

  def getId: Try[String] = Success(id)
}

case class BlockingQueueReader[T](queueOps: BlockingQueueOps[T]) extends QueueReader[BlockingQueue[T]] {
  
  def receiveMessage = Try {
    val (id, body) = queueOps.rawQueue.take()
    SimpleMessage(id, body)
  }
}

case class BlockingQueueWriter[T](queueOps: BlockingQueueOps[T]) extends QueueWriter[BlockingQueue[T]] {

  def writeRaw(values: List[(String, T)]) = Try {

    values.foreach(queueOps.rawQueue.put)
  }
}

case class BlockingQueueOps[B](queue: BlockingQueue[B], size: Int) extends QueueOps[BlockingQueue[B]] { blockingQueueOps =>

  type Reader = BlockingQueueReader[B]
  type Writer = BlockingQueueWriter[B]

  def deleteMessage(message: SimpleMessage[B]) = Success(())

  def reader: Try[BlockingQueueReader[B]] = Success(new BlockingQueueReader(blockingQueueOps))

  def writer: Try[BlockingQueueWriter[B]] = Success(new BlockingQueueWriter(blockingQueueOps))

  def isEmpty: Boolean = rawQueue.isEmpty

  def delete() = Success(())

  val rawQueue = new ArrayBlockingQueue[(String, B)](size)
}

case class BlockingQueue[B](qname: String, size: Int) extends Queue[Unit](qname) with AnyQueueManager { 

  blockingQueue =>

  type Queue = BlockingQueue[B]
  type QueueOps = BlockingQueueOps[B]
  type Message = SimpleMessage[B]
  type Reader = BlockingQueueReader[B]
  type Writer = BlockingQueueWriter[B]

  def create(ctx: Context): Try[QueueOps] = Success(BlockingQueueOps[B](blockingQueue, size))
}
