package ohnosequences.compota.queues

import scala.util.Try


trait QueueAux {
  type Elements

  type Message[E] <: QueueMessage[E]


  trait QueueMessage[E] {
    def getBody: Try[E]
  }

  def deleteMessage(message: Message[Elements]): Try[Unit]

  trait QueueReader {
    def getMessage: Try[Message[Elements]]
  }

  type QR <: QueueReader
  type QW <: QueueWriter

  trait QueueWriter {
    def write(values: List[Elements]): Try[Unit]
  }

  def getReader: Try[QR]
  def getWriter: Try[QW]
}





abstract class Queue[E] extends QueueAux {
  type Elements = E
}
