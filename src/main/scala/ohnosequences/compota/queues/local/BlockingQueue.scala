package ohnosequences.compota.queues.local

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.queues._

import scala.util.{Try, Success}


case class SimpleMessage[E](val id: String, body: E) extends QueueMessage[E] {
  override def getBody: Try[E] = Success(body)

}

class BlockingQueueReader[T](queueOps: BlockingQueueOp[T]) extends QueueReader[T, SimpleMessage[T]] {
  override def receiveMessage = Try {
    //println("here")
    val (id, body) = queueOps.queue.rawQueue.take()
    SimpleMessage(id, body)
  }
}

class BlockingQueueWriter[T](queue: BlockingQueueOp[T]) extends QueueWriter[T] {

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]

  override def writeRaw(values: List[(String, T)]) = Try {

    values.foreach(queue.queue.rawQueue.put)
  }
}

class BlockingQueueOp[T](val queue: BlockingQueue[T]) extends QueueOp[T, SimpleMessage[T], BlockingQueueReader[T], BlockingQueueWriter[T]] { blockingQueueOps =>


  override def deleteMessage(message: SimpleMessage[T]) = Success(())



  override def reader: Try[BlockingQueueReader[T]] = Success(new BlockingQueueReader(blockingQueueOps))

  override def writer: Try[BlockingQueueWriter[T]] = Success(new BlockingQueueWriter(blockingQueueOps))

  override def isEmpty: Boolean = queue.rawQueue.isEmpty

  override def delete() = Success(())


  override def size: Int = {
    queue.rawQueue.size()
  }

}

class BlockingQueue[T](name: String, size: Int) extends Queue[T, Unit](name) { blockingQueue =>

  val rawQueue = new ArrayBlockingQueue[(String, T)](size)

  type Msg = SimpleMessage[T]

  type Reader = BlockingQueueReader[T]
  type Writer = BlockingQueueWriter[T]

  override def create(ctx: Context): Try[QueueOp[T, SimpleMessage[T], BlockingQueueReader[T], BlockingQueueWriter[T]]] = Success(new BlockingQueueOp[T](blockingQueue))
}
