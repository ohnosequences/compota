package ohnosequences.compota.queues

import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.environment.Env
import ohnosequences.compota.monoid.Monoid
import ohnosequences.logging.Logger

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait AnyQueueMessage {
  type QueueMessageElement

  def getBody: Try[Option[QueueMessageElement]]

  val id: String
}

object AnyQueueMessage {
  type of[E] = AnyQueueMessage {type QueueMessageElement = E}
}

abstract class QueueMessage[E] extends AnyQueueMessage {
  override type QueueMessageElement = E
}


trait AnyQueueReader {

  type QueueReaderElement

  type QueueReaderMessage <: AnyQueueMessage.of[QueueReaderElement]

  val queueOp: AnyQueueOp

  def receiveMessage(logger: Logger): Try[Option[QueueReaderMessage]]

  def waitForMessage(logger: Logger,
                     isStopped: => Boolean = {
                       false
                     },
                     initialTimeout: Duration = Duration(100, MILLISECONDS)
                      ): Try[Option[QueueReaderMessage]] = {

    @tailrec
    def waitForMessageRep(timeout: Long): Try[Option[QueueReaderMessage]] = {
      if (isStopped) {
        Success(None)
      } else {
        Try {
          receiveMessage(logger)
        }.flatMap { e => e } match {
          case Failure(t) if isStopped => {
            Success(None)
          }
          case Failure(t) => {
            Failure(t)
          }
          case Success(None) => {
            logger.debug("queue reader for " + queueOp.queue.name + " waiting for message")
            Thread.sleep(timeout)
            waitForMessageRep(timeout * 3 / 2)
          }
          case Success(Some(message)) => {
            Success(Some(message))
          }
        }
      }
    }

    waitForMessageRep(initialTimeout.toMillis)
  }
}


object AnyQueueReader {
  type of[E, M <: AnyQueueMessage.of[E]] = AnyQueueReader {
    type QueueReaderElement = E
    type QueueReaderMessage = M
  }
}

abstract class QueueReader[E, M <: AnyQueueMessage.of[E]] extends AnyQueueReader {
  override type QueueReaderElement = E
  override type QueueReaderMessage = M
}

trait AnyQueueWriter {
  type QueueWriterElement

  def writeRaw(values: List[(String, QueueWriterElement)]): Try[Unit]

  def writeMessages(prefixId: String, values: List[QueueWriterElement]): Try[Unit] = {
    writeRaw(values.zipWithIndex.map { case (value, i) =>
      //  println("zip generated " + prefixId + "." + i)
      (prefixId + "_" + i, value)
    })
  }
}

object AnyQueueWriter {
  type of[E] = AnyQueueWriter {type QueueWriterElement = E}
}

abstract class QueueWriter[E] extends AnyQueueWriter {
  override type QueueWriterElement = E
}

trait AnyQueue { queue =>

  type QueueContext

  def name: String

  type QueueElement

  type QueueQueueMessage <: AnyQueueMessage.of[QueueElement]

  type QueueQueueReader <: AnyQueueReader.of[QueueElement, QueueQueueMessage]
  type QueueQueueWriter <: AnyQueueWriter.of[QueueElement]

  type QueueQueueOp <: AnyQueueOp.of[QueueElement, QueueQueueMessage, QueueQueueReader, QueueQueueWriter]

  def create(ctx: QueueContext): Try[QueueQueueOp]

  def delete(ctx: QueueContext): Try[Unit] = {
    create(ctx).flatMap(_.delete())
  }

  def subQueues: List[AnyQueue] = List(queue)

  def reduce(env: Env, queueOp: AnyQueueOp.of1[QueueElement]): Try[Unit] = {
    Success(())
  }


}

trait AnyMonoidQueue extends AnyQueue { monoidQueue =>
  val monoid: Monoid[QueueElement]

  def reducer: Option[AnyQueueReducer.of[QueueElement]]

  override def reduce(env: Env, queueOp: AnyQueueOp.of1[QueueElement]): Try[Unit] = {
    reducer match {
      case None => Success(())
      case Some(r) => r.reduce(env, monoidQueue, queueOp)
    }
  }

}


object AnyQueue {
  type of[Ctx] = AnyQueue {type QueueContext = Ctx}

  type of2[E, Ctx] = AnyQueue {
    type QueueContext = Ctx
    type QueueElement = E
  }

  type of3[E, Ctx, Msg <: AnyQueueMessage.of[E]] = AnyQueue {
    type QueueContext = Ctx
    type QueueElement = E
    type QueueQueueMessage = Msg
  }

  type ofm[E] = AnyMonoidQueue {
    type QueueElement = E
  }

  type of1[E] = AnyQueue {
    type QueueElement = E
  }

  type of2m[E, Ctx] = AnyMonoidQueue {
    type QueueContext = Ctx
    type QueueElement = E
  }




  //  type of2[E, Ctx, M <: AnyQueueMessage.of[E], R <: AnyQueueReader.of[E, M], W <: AnyQueueWriter.of[E]] = AnyQueue {
  //    type QueueContext = Ctx
  //    type QueueElement = E
  //    type QueueQueueMessage = M
  //    type QueueQueueReader = R
  //    type QueueQueueWriter = W
  //  }

  type of6[E, Ctx, M <: AnyQueueMessage.of[E], R <: AnyQueueReader.of[E, M], W <: AnyQueueWriter.of[E], O <: AnyQueueOp.of[E, M, R, W]] = AnyQueue {
    type QueueContext = Ctx
    type QueueElement = E
    type QueueQueueMessage = M
    type QueueQueueReader = R
    type QueueQueueWriter = W
    type QueueQueueOp = O
  }
}

abstract class Queue[E, Ctx](val name: String) extends AnyQueue {
  override type QueueElement = E
  override type QueueContext = Ctx
}


trait AnyQueueOp { anyQueueOp =>

  def subOps(): List[AnyQueueOp] = List(AnyQueueOp.this)

  type QueueOpElement
  type QueueOpQueueMessage <: AnyQueueMessage.of[QueueOpElement]
  // QueueMessage[QueueOpElement]
  type QueueOpQueueReader <: AnyQueueReader.of[QueueOpElement, QueueOpQueueMessage]
  // QueueReader[QElement, QMessage]
  type QueueOpQueueWriter <: AnyQueueWriter.of[QueueOpElement] //QueueWriter[QElement]

  val queue: AnyQueue.of1[QueueOpElement]

  def reduce(env: Env): Try[Unit] = {
    queue.reduce(env, anyQueueOp)
  }

  def isEmpty: Try[Boolean]

  def get(key: String): Try[QueueOpElement]

  def getContent(key: String): Try[Either[URL, String]] = {
    get(key).map { r =>
      Right(r.toString)
    }
  }

  def list(lastKey: Option[String], limit: Option[Int] = None): Try[(Option[String], List[String])]

  def deleteMessage(message: QueueOpQueueMessage): Try[Unit]

  def reader: Try[QueueOpQueueReader]

  def writer: Try[QueueOpQueueWriter]

  def delete(): Try[Unit]

  def foldLeftIdIndexed[T](z: T)(f: (T, String, Int) => Try[T]): Try[T] = {
    @tailrec
    def foldLeftIdRec(c: T, lastKey: Option[String], index: Int): Try[T] = {
      //println("lastKey: " + lastKey + " index: " + index)
      list(lastKey = lastKey, limit = None) match {
        case Success((_, Nil)) => {
          //the last chunk
          Success(c)
        }
        case Success((None, list)) => {
          //the last chunk
          list.zipWithIndex.foldLeft[Try[T]](Success(c)) {
            case (Failure(t), (id, ind)) => Failure(t)
            case (Success(t), (id, ind)) => f(t, id, ind)
          }
        }
        case Success((last, list)) => {
          list.zipWithIndex.foldLeft[Try[T]](Success(c)) {
            case (Failure(t), (id, ind)) => Failure(t)
            case (Success(t), (id, ind)) => f(t, id, ind)
          } match {
            case Success(newC) => foldLeftIdRec(newC, last, index + list.size)
            case failure => failure
          }
        }
        case Failure(t) => Failure(t)
      }
    }
    foldLeftIdRec(z, None, 0)
  }

  def foldLeftId[T](z: T)(f: (T, String) => Try[T]): Try[T] = {
    foldLeftIdIndexed(z) { case (t, id, index) =>
      f(t, id)
    }

  }

  def foldLeftIndexed[T](z: T)(f: (T, QueueOpElement, Int) => Try[T]): Try[T] = {
    foldLeftIdIndexed(z) { case (c, id, index) =>
      get(id).flatMap { el =>
        f(c, el, index)
      }
    }
  }

  def foldLeft[T](z: T)(f: (T, QueueOpElement) => Try[T]): Try[T] = {
    foldLeftId(z) { case (c, id) =>
      get(id).flatMap { el =>
        f(c, el)
      }
    }
  }

  def forEachId(f: String => Unit): Try[Unit] = {
    foldLeftId(()) { case (u, id) =>
      Try{f(id)}
    }
  }

  def forEach(f: (String, QueueOpElement) => Unit): Try[Unit] = {
    forEachId { id =>
      f(id, get(id).get)
    }
  }

  def size: Try[Int]

}

object  AnyQueueOp {
  type of[E, M <: AnyQueueMessage.of[E], QR <: AnyQueueReader.of[E, M], QW <: AnyQueueWriter.of[E]] = AnyQueueOp {
    type QueueOpElement = E
    type QueueOpQueueMessage = M
    type QueueOpQueueReader = QR
    type QueueOpQueueWriter = QW
  }
  type of2[E, M <: AnyQueueMessage.of[E]] = AnyQueueOp {
    type QueueOpElement = E
    type QueueOpQueueMessage = M
  }

  type of1[E] = AnyQueueOp {
    type QueueOpElement = E
  }
}


abstract class QueueOp[E, M <: AnyQueueMessage.of[E], QR <: AnyQueueReader.of[E, M], QW <: AnyQueueWriter.of[E]] extends AnyQueueOp {

  override type QueueOpElement = E
  override type QueueOpQueueMessage = M
  override type QueueOpQueueReader = QR
  override type QueueOpQueueWriter = QW

}

//abstract class Queue[E, Ctx](val name: String) extends AnyQueue {
//  type Elmnt = E
//  type Context = Ctx
//}

//object Queue {
//  type of[E, Ctx, M <: QueueMessage[E], R <: QueueReader[E, M], W <: QueueWriter[E]] = Queue[E, Ctx] {
//    type Msg = M
//    type Reader = R
//    type Writer = W
//  }
//}

//trait AnyReducibleQueue extends AnyQueue {
//  val monoid: Monoid[Elmnt]
//
//  def reduce(environment: Environment[Context]): Try[Unit] = {
//    Success(())
//  }
//}

