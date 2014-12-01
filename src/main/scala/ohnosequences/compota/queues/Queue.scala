package ohnosequences.compota.queues

import ohnosequences.compota.monoid.Monoid

import scala.util.{Success, Try}


trait QueueMessage[E] {
  def getBody: Try[E]
  def getId: Try[String] //parse id error?
}


trait QueueReader[E, M <: QueueMessage[E]] {
  def receiveMessage: Try[M]
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

  type Context

  val name: String

  type Message <: QueueMessage[Element]

  type QR <: QueueReader[Element, Message]
  type QW <: QueueWriter[Element, Message]

  def create(ctx: Context): Try[QueueOp[Element, Message, QR, QW]]
}


trait QueueOpAux {
  type Element
  type Message <: QueueMessage[Element]
  type QRR <: QueueReader[Element, Message]
  type QWW <: QueueWriter[Element, Message]

  def deleteMessage(message: Message): Try[Unit]

  def getReader: Try[QRR]
  def getWriter: Try[QWW]

  def isEmpty: Boolean

  def delete(): Try[Unit]
}

abstract class QueueOp[E, M <: QueueMessage[E], QR <: QueueReader[E, M], QW <: QueueWriter[E, M]] extends QueueOpAux {

  type Element = E
  type Message = M
  type QRR = QR
  type QWW = QW


}

//class UnitQueueOp[E] extends QueueOp[E] {
//  override def test(): E = ???
//}


abstract class Queue[E, Ctx](val name: String) extends QueueAux {
  type Element = E
  type Context = Ctx
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