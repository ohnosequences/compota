package ohnosequences.compota.queues

import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.logging.Logger

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


//trait AnyMessage {
//
//  type Body
//  def getBody: Try[Body]
//  // TODO: a better id type
//  def getId: Try[String] //parse id error?
//}


object QueueMessage {
  val terminateID = "terminate-id"
}

abstract class QueueMessage[B] {
  def getBody: Try[B]
  //  // TODO: a better id type
  val id: String
}
// TODO: they should be defined for queues, not for messages
//trait AnyQueueReader {
//
//  type Message <: QueueMessage
//  def receiveMessage: Try[Message]
//}

abstract class QueueReader[E, M <: QueueMessage[E]] {

  val queueOp: AnyQueueOp

  def receiveMessage(logger: Logger): Try[Option[M]]

  def waitForMessage(logger: Logger,
                     isStopped: => Boolean = {false},
                     initialTimeout: Duration = Duration(100, MILLISECONDS)
                      ): Try[Option[M]] = {

    @tailrec
    def waitForMessageRep(timeout: Long): Try[Option[M]] = {
      if(isStopped) {
        Success(None)
      } else {
        Try {receiveMessage(logger)}.flatMap{e => e}  match {
          case Failure(t) if isStopped => {
            Success(None)
          }
          case Failure(t)=> {
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

abstract class QueueWriter[E] {

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]
  def writeRaw(values: List[(String, E)]): Try[Unit]

  def writeMessages(prefixId: String, values: List[E]): Try[Unit] = {
    writeRaw(values.zipWithIndex.map { case (value, i) =>
    //  println("zip generated " + prefixId + "." + i)
      (prefixId + "_" + i, value)
    })
  }

}

trait AnyQueue { queue =>
  
  type Context

  val name: String

  // not needed here
  type Elmnt

  type Msg <: QueueMessage[Elmnt]



  type Reader <: QueueReader[Elmnt, Msg]
  type Writer <: QueueWriter[Elmnt]

  // why?? put it somewhere else, not here. At the nispero level, for example
   def create(ctx: Context): Try[QueueOp[Elmnt, Msg, Reader, Writer]]

   def delete(ctx: Context): Try[Unit] = {
     create(ctx).flatMap(_.delete())
   }
}

object AnyQueue {
  type of[Ctx] = AnyQueue { type Context = Ctx}
}


trait AnyQueueOp {



  type QElement
  type QMessage <: QueueMessage[QElement]
  type Reader <: QueueReader[QElement, QMessage]
  type Writer <: QueueWriter[QElement]

  val queue: AnyQueue

  def isEmpty: Try[Boolean]

  def delete(): Try[Unit]

}

// all these types are here just for convenience
abstract class QueueOp[E, M <: QueueMessage[E], QR <: QueueReader[E, M], QW <: QueueWriter[E]] extends AnyQueueOp { queueOp =>

  def deleteMessage(message: M): Try[Unit]

  def reader: Try[QR]
  def writer: Try[QW]



  def delete(): Try[Unit]

  def list(lastKey: Option[String], limit: Option[Int] = None): Try[(Option[String], List[String])]

  def read(key: String): Try[E]

  def forEachId[T](f: String => T): Try[Unit] = {

    @tailrec
    def forEachRec(f: String => T, lastKey: Option[String]): Try[Unit] = {
      list(lastKey = None, limit = None) match {
        case Success((None, list)) => {
          //the last chunk
          Success(list.foreach(f))
        }
        case Success((last, list)) => {
          list.foreach(f)
          forEachRec(f, last)
        }
        case Failure(t) => Failure(t)
      }
    }

    forEachRec(f, None)

  }

  def forEach[T](f: (String, E) => T): Try[Unit] = {
    Try {
      forEachId { id =>
        f(id, read(id).get)
      }
    }
  }

  def size: Try[Int]
}

abstract class Queue[E, Ctx](val name: String) extends AnyQueue {
  type Elmnt = E
  type Context = Ctx
}

//trait AnyReducibleQueue extends AnyQueue {
//  val monoid: Monoid[Elmnt]
//
//  def reduce(environment: Environment[Context]): Try[Unit] = {
//    Success(())
//  }
//}

