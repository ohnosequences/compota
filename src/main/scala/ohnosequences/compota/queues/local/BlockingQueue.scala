package ohnosequences.compota.queues.local

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.queues._

import scala.util.{Try, Success}


case class SimpleMessage[E](id: String, body: E) extends QueueMessage[E] {
  override def getBody: Try[E] = Success(body)

  override def getId: Try[String] = Success(id)
}

class BlockingQueueReader[T](queueOps: BlockingQueueOps[T]) extends QueueReader[T, SimpleMessage[T]] {
  override def receiveMessage = Try {
    val (id, body) = queueOps.rawQueue.take()
    SimpleMessage(id, body)
  }
}

class BlockingQueueWriter[T](queue: BlockingQueueOps[T]) extends QueueWriter[T] {

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]

  override def writeRaw(values: List[(String, T)]) = Try {

    values.foreach(queue.rawQueue.put)
  }
}

class BlockingQueueOps[T](queue: BlockingQueue[T], size: Int) extends QueueOps[T, SimpleMessage[T], BlockingQueueReader[T], BlockingQueueWriter[T]] { blockingQueueOps =>

  override def deleteMessage(message: SimpleMessage[T]) = Success(())



  override def reader: Try[BlockingQueueReader[T]] = Success(new BlockingQueueReader(blockingQueueOps))

  override def writer: Try[BlockingQueueWriter[T]] = Success(new BlockingQueueWriter(blockingQueueOps))

  override def isEmpty: Boolean = rawQueue.isEmpty

  override def delete() = Success(())


  val rawQueue = new ArrayBlockingQueue[(String, T)](size)
}

class BlockingQueue[T](name: String, size: Int) extends Queue[T, Unit](name) { blockingQueue =>

  type Msg = SimpleMessage[T]

  type Reader = BlockingQueueReader[T]
  type Writer = BlockingQueueWriter[T]

  override def create(ctx: Context): Try[QueueOps[T, SimpleMessage[T], BlockingQueueReader[T], BlockingQueueWriter[T]]] = Success(new BlockingQueueOps[T](blockingQueue, size))
}
