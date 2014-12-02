package ohnosequences.compota.queues

import ohnosequences.compota.monoid.Monoid

import scala.util.{Success, Try}


trait AnyMessage {

  type Body
  def getBody: Try[Body]
  // TODO: a better id type
  def getId: Try[String] //parse id error?
}

// TODO: they should be defined for queues, not for messages
trait AnyQueueReader {

  type Message <: AnyMessage
  def receiveMessage: Try[Message]
}

trait AnyQueueWriter {

  type Message <: AnyMessage

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]
  def writeRaw(values: List[(String, Message)]): Try[Unit]

  final def writeMessages(prefixId: String, values: List[E]): Try[Unit] = {
    writeRaw(values.zipWithIndex.map { case (value, i) =>
      (prefixId + "." + i, value)
    })
  }
}

trait AnyQueue { queue =>
  
  type Context

  val name: String

  type Message <: AnyMessage

  // not needed here
  type Body = Message#Body

  type Reader <: AnyQueueReader { type Message <: queue.Message }
  type Writer <: AnyQueueWriter { type Message <: queue.Message }

  // why?? put it somewhere else, not here. At the nispero level, for example
  // def create(ctx: Context): Try[QueueOps[Element, Message, QR, QW]]
}


trait AnyQueueOps {

  // extract everything from here
  type Queue <: AnyQueue

  def deleteMessage(message: Message): Try[Unit]

  def reader: Try[Queue#Reader]
  def writer: Try[Queue#Writer]

  def isEmpty: Boolean

  def delete(): Try[Unit]
}

// all these types are here just for convenience
abstract class QueueOps[Q <: AnyQueue] extends AnyQueueOps {

  type Queue = Q
}

abstract class Queue[E, Ctx](val name: String) extends AnyQueue {
  type Element = E
  type Context = Ctx
}

trait AnyMonoidQueue extends AnyQueue {

  def monoid: Monoid[Message#Body]
  // TODO: what is this???
  def reduce: Try[Unit] = {
    Success(())
  }
}

//is it needed?
// trait MonoidQueue[E] extends MonoidQueueAux {
//   override type Element = E

// }