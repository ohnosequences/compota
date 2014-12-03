package ohnosequences.compota.queues

import ohnosequences.compota.monoid.Monoid

import scala.util.{Success, Try}


//trait AnyMessage {
//
//  type Body
//  def getBody: Try[Body]
//  // TODO: a better id type
//  def getId: Try[String] //parse id error?
//}


abstract class QueueMessage[B] {
  def getBody: Try[B]
  //  // TODO: a better id type
  def getId: Try[String]
}
// TODO: they should be defined for queues, not for messages
//trait AnyQueueReader {
//
//  type Message <: QueueMessage
//  def receiveMessage: Try[Message]
//}

abstract class QueueReader[E, M <: QueueMessage[E]] {
  def receiveMessage: Try[M]

}
//
//trait AnyQueueWriter {
//
//  //type Message <: AnyMessage
//
//
//}

abstract class QueueWriter[E] {

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]
  def writeRaw(values: List[(String, E)]): Try[Unit]

  def writeMessages(prefixId: String, values: List[E]): Try[Unit] = {
    writeRaw(values.zipWithIndex.map { case (value, i) =>
      (prefixId + "." + i, value)
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
   def create(ctx: Context): Try[QueueOps[Elmnt, Msg, Reader, Writer]]
}


trait AnyQueueOps {



  type QElement
  type QMessage <: QueueMessage[QElement]
  type Reader <: QueueReader[QElement, QMessage]
  type Writer <: QueueWriter[QElement]

  def delete(): Try[Unit]

}

// all these types are here just for convenience
abstract class QueueOps[E, M <: QueueMessage[E], QR <: QueueReader[E, M], QW <: QueueWriter[E]] extends AnyQueueOps {

  def deleteMessage(message: M): Try[Unit]

  def reader: Try[QR]
  def writer: Try[QW]

  def isEmpty: Boolean

  def delete(): Try[Unit]

}

abstract class Queue[E, Ctx](val name: String) extends AnyQueue {
  type Elmnt = E
  type Context = Ctx
}

trait AnyMonoidQueue extends AnyQueue {

  def monoid: Monoid[Elmnt]
  // TODO: what is this???
  def reduce: Try[Unit] = {
    Success(())
  }
}

//is it needed?
// trait MonoidQueue[E] extends MonoidQueueAux {
//   override type Element = E

// }