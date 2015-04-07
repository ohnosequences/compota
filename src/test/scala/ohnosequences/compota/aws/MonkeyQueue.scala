package ohnosequences.compota.aws

import java.util.concurrent.ArrayBlockingQueue

import ohnosequences.compota.monoid.Monoid
import ohnosequences.compota.queues._
import ohnosequences.compota.queues.local.BlockingQueueWriter

import scala.util.{Success, Failure, Random, Try}

//class BlockingQueue[T](name: String, size: Int, val monoid: Monoid[T]) extends Queue[T, Unit](name)
class MonkeyQueue[T](name: String, size: Int, val monoid: Monoid[T]) extends Queue[T, Unit](name) { queue =>

  val blockingQueue = new ArrayBlockingQueue[Option[(String, T)]](1000)
  override type Msg = MonkeyMessage[T]

  override type Writer = MonkeyWriter[T]
  override type Reader = MonkeyReader[T]

  override def create(ctx: Context): Try[MonkeyQueueOp[T]] = {
    Success(new MonkeyQueueOp[T](queue))
  }

}

case class MonkeyMessage[T](id: String, body: T, empty: Boolean) extends QueueMessage[T] {
  override def getBody: Try[T] = {
    if(Random.nextInt(100) % 5 == 1) {
      Failure(new Error("monkey was here"))
    } else if (empty) {
      Failure(new Error("empty message"))
    } else {
      Success(body)
    }
  }
}


class MonkeyReader[T](val queueOp: MonkeyQueueOp[T]) extends QueueReader[T, MonkeyMessage[T]] {
  override def receiveMessage: Try[MonkeyMessage[T]] = {
    try {
      queueOp.queue.blockingQueue.take() match {
        case None => Success(MonkeyMessage("_", queueOp.queue.monoid.unit, true))
        case Some((id, value)) => {
          queueOp.queue.blockingQueue.put(Some((id, value)))
          Success(MonkeyMessage(id, value, false))
        }
      }
    } catch {
      case e: InterruptedException => Failure(e)
    }
  }
}

class MonkeyWriter[T](val queueOp: MonkeyQueueOp[T]) extends QueueWriter[T] {
  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]
  override def writeRaw(values: List[(String, T)]): Try[Unit] = {
    if (Random.nextInt(100) % 5 == -1 ) {
      Failure(new Error("monkey was here"))
    } else {
     // println("writing " + values)
      Success(values.foreach { value =>
        queueOp.queue.blockingQueue.put(Some(value))
      })
    }
  }
}


//[E, M <: QueueMessage[E], QR <: QueueReader[E, M], QW <: QueueWriter[E]]
class MonkeyQueueOp[T](val queue: MonkeyQueue[T]) extends QueueOp[T, MonkeyMessage[T], MonkeyReader[T], MonkeyWriter[T]] {  queueOp =>
  override def deleteMessage(message: MonkeyMessage[T]): Try[Unit] = {
    queue.blockingQueue.remove(Some((message.id, message.body)))
    Success(())
  }

  def writeEmptyMessage(): Unit = {
    queue.blockingQueue.put(None)
  }


  override def size: Int = queue.blockingQueue.size()

  override def writer: Try[MonkeyWriter[T]] =  Success(new MonkeyWriter[T](queueOp))

  override def reader: Try[MonkeyReader[T]] = Success(new MonkeyReader[T](queueOp))

  override def delete(): Try[Unit] = {
    Success(())
  }

  override def isEmpty: Boolean = queue.blockingQueue.isEmpty

}
