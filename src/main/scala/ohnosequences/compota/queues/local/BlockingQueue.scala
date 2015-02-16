package ohnosequences.compota.queues.local

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.monoid.Monoid
import ohnosequences.compota.queues._

import scala.util.{Failure, Try, Success}


case class SimpleMessage[E](id: String, body: E, empty: Boolean = false) extends QueueMessage[E] {
  override def getBody: Try[E] = {
    if (!empty) {
      Success(body)
    } else {
      Failure(new Error("got empty message"))
    }
  }
}



class BlockingQueueReader[T](queueOps: BlockingQueueOp[T]) extends QueueReader[T, SimpleMessage[T]] {
  override def receiveMessage: Try[SimpleMessage[T]] = try {
    //println("here")
    queueOps.queue.rawQueue.take() match {
      case None => Success(SimpleMessage("_", queueOps.queue.monoid.unit, true))
      case Some((id, body)) => Success(SimpleMessage(id, body))
    }
  } catch {
    case e: InterruptedException => Failure(e)
  }
}

class BlockingQueueWriter[T](queue: BlockingQueueOp[T]) extends QueueWriter[T] {

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]

  override def writeRaw(values: List[(String, T)]) = Try {

    values.foreach { value =>
      queue.queue.rawQueue.put(Some(value))
    }
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

class BlockingQueue[T](name: String, size: Int, val monoid: Monoid[T]) extends Queue[T, Unit](name) { blockingQueue =>

  val rawQueue = new ArrayBlockingQueue[Option[(String, T)]](size)

  type Msg = SimpleMessage[T]

  type Reader = BlockingQueueReader[T]
  type Writer = BlockingQueueWriter[T]

  def writeEmptyMessage(): Unit = {
    rawQueue.put(None)
  }

  override def create(ctx: Context): Try[QueueOp[T, SimpleMessage[T], BlockingQueueReader[T], BlockingQueueWriter[T]]] = Success(new BlockingQueueOp[T](blockingQueue))
}
