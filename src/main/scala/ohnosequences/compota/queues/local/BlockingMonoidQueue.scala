//package ohnosequences.compota.queues.local
//
//import ohnosequences.compota.monoid.Monoid
//import ohnosequences.compota.queues.{MonoidQueueAux, QueueWriter, Queue, MonoidQueue}
//
//import scala.util.{Success, Try}
//
//
//class BlockingMonoidQueue[T](name: String, size: Int, val monoid: Monoid[T]) extends
// Queue[T](name) with MonoidQueueAux {
//  val queue = new BlockingQueue[T](name, size)
//
//  override type Message = queue.Message
//
//  override type QW = MonoidBlockingQueueWriter
//  override type QR = queue.QR
//
//  class MonoidBlockingQueueWriter(writer: queue.QW) extends QueueWriter[T, SimpleMessage[T]] {
//
//    override def writeRaw(values: List[(String, T)]): Try[Unit] = {
//      writer.writeRaw(values.filterNot(_._2.equals(monoid.unit)))
//
//    }
//  }
//
//  override def deleteMessage(message: Message): Try[Unit] = queue.deleteMessage(message)
//
//  override def getWriter: Try[QW] = Try(new MonoidBlockingQueueWriter(queue.getWriter.get))
//
//  override def isEmpty: Boolean = queue.isEmpty
//
//  override def getReader: Try[QR] = queue.getReader
//
//  override def delete(): Try[Unit] = Success(())
//
//
//
//}
