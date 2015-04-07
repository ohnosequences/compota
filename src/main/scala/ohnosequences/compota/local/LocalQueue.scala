package ohnosequences.compota.local

import java.util.concurrent.{ConcurrentHashMap}

import ohnosequences.compota.queues.{QueueWriter, QueueReader, QueueOp, Queue}

import scala.util.{Failure, Success, Try}

class LocalQueue[T](name: String) extends Queue[T, Unit](name) { queue =>

  val rawQueue = new ConcurrentHashMap[String, T]()

  type Msg = SimpleMessage[T]

  type Reader = LocalQueueReader[T]
  type Writer = LocalQueueWriter[T]

  override def create(ctx: Unit): Try[QueueOp[T, SimpleMessage[T], LocalQueueReader[T], LocalQueueWriter[T]]] =
    Success(new LocalQueueOp[T](queue))
}

class LocalQueueOp[T](val queue: LocalQueue[T]) extends QueueOp[T, SimpleMessage[T], LocalQueueReader[T], LocalQueueWriter[T]] { queueOp =>

  override def deleteMessage(message: SimpleMessage[T]) = Try {
    queue.rawQueue.remove(message.id)
  }

  override def reader: Try[BlockingQueueReader[T]] = Success(new BlockingQueueReader(blockingQueueOps))

  override def writer: Try[BlockingQueueWriter[T]] = Success(new BlockingQueueWriter(blockingQueueOps))

  override def isEmpty: Boolean = queue.rawQueue.isEmpty

  override def delete() = Success(())


  override def listChunk(lastKey: Option[String]): (Option[String], List[String]) = {
    queue.rawQueue.
  }

  override def size: Int = {
    queue.rawQueue.size()
  }

}

class LocalQueueReader[T](queueOps: LocalQueueOp[T]) extends QueueReader[T, SimpleMessage[T]] {


  override def receiveMessage: Try[SimpleMessage[T]] = Try {

    if()
    queueOps.queue.rawQueue. match {
      case None => Success(SimpleMessage("_", queueOps.queue.monoid.unit, true))
      case Some((id, body)) => Success(SimpleMessage(id, body))
    }
  } catch {
    case e: InterruptedException => Failure(e)
  }
}

class LocalQueueWriter[T](queue: BlockingQueueOp[T]) extends QueueWriter[T] {

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]

  override def writeRaw(values: List[(String, T)]) = Try {

    values.foreach { value =>
      queue.queue.rawQueue.put(Some(value))
    }
  }
}

