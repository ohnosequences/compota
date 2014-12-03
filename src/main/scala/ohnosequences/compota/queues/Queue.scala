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


trait AnyMessage {

  type Body
  // TODO: move all this to ops; Try does not make sense here
  def getBody: Try[Body]
  // TODO: a better id type
  def getId: Try[String]
}
trait Message[B] extends AnyMessage { type Body = B }

trait AnyQueue { queue =>
  
  // TODO: move this _out_ of here
  // it is only ever used for creation, which should not be coupled with declaring this queue
  type Context

  val name: String

  type Message <: AnyMessage
}

trait AnyQueueManager { qm =>

  type Context
  type Queue <: AnyQueue
  type QueueOps <: AnyQueueOps { type Queue <: qm.Queue }

  def create(ctx: Context): Try[QueueOps]
}


trait AnyQueueOps { qops =>

  type Queue <: AnyQueue

  type Reader <: AnyQueueReader { type Queue <: qops.Queue }
  type Writer <: AnyQueueWriter { type Queue <: qops.Queue }
  
  def delete(): Try[Unit]

  def reader: Try[Reader]
  def writer: Try[Writer]

  def isEmpty: Boolean
}

// all these types are here just for convenience
trait QueueOps[Q <: AnyQueue] extends AnyQueueOps {

  type Queue = Q

  // TODO: why here?? if you are declaring writer ops, move it there.
  def deleteMessage(message: Queue#Message): Try[Unit]
}

abstract class Queue[Ctx](val name: String) extends AnyQueue {

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


trait AnyQueueReader {

  type Queue <: AnyQueue

  def receiveMessage: Try[Queue#Message]
}

trait QueueReader[Q <: AnyQueue] extends AnyQueueReader { type Queue = Q }

trait AnyQueueWriter {

  type Queue <: AnyQueue

  def writeRaw(values: List[(String, Queue#Message#Body)]): Try[Unit]

  // TODO: this signature is wrong: you should write messages, not their bodies
  final def writeMessages(prefixId: String, values: List[Queue#Message#Body]): Try[Unit] = {

    writeRaw(values.zipWithIndex.map { case (value, i) =>
      (prefixId + "." + i, value)
    })
  }
}

trait QueueWriter[Q <: AnyQueue] extends AnyQueueWriter { type Queue = Q }