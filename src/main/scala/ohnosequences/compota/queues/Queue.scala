package ohnosequences.compota.queues

import ohnosequences.compota.monoid.Monoid

import scala.util.{Success, Try}


trait QueueMessage[E] {
  def getBody: Try[E]
  def getId: Try[String] //parse id error?
}


trait QueueReader[E, M <: QueueMessage[E]] {
  def getMessage: Try[M]
}

trait QueueWriter[E, M <: QueueMessage[E]] {
  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]
  def writeRaw(values: List[(String, E)]): Try[Unit]

  def write(prefixId: String, values: List[E]): Try[Unit] = {
    writeRaw(values.zipWithIndex.map { case (value, i) =>
      (prefixId + "." + i, value)
    })
  }

}

trait QueueAux {
  type Element

  type Message <: QueueMessage[Element]

  def deleteMessage(message: Message): Try[Unit]

  type QR <: QueueReader[Element, Message]
  type QW <: QueueWriter[Element, Message]

  def getReader: Try[QR]
  def getWriter: Try[QW]

  val name: String

  def isEmpty: Boolean
}



abstract class Queue[E](val name: String) extends QueueAux {
  type Element = E
}

trait MonoidQueueAux extends QueueAux {
  val monoid: Monoid[Element]
  def reduce: Try[Unit] = {
    Success(())
  }
}

//is it needed?
trait MonoidQueue[E] extends MonoidQueueAux {
  override type Element = E

}