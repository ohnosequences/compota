package ohnosequences.compota.queues

import ohnosequences.compota._


trait MonoidQueueAux {

  type MA

  val monoid: Monoid[MA]

  val serializer: Serializer[MA]

  val merger: QueueMerger[MA]

  val name: String

  def initRead()

  def initWrite()

  def put(parentId: String, nispero: String, values: List[MA])

  def read(): Message[MA]

  def reset()

  def sqsQueueInfo(): Option[SQSQueueInfo]

  def isEmpty: Boolean

  def list(): List[String]

  def read(id: String): Option[MA]

  def readRaw(id: String): Option[String] = {
    read(id).map(serializer.toString)
  }

  def delete(id: String)

  def delete()

  def listChunk(limit: Int, lastKey: Option[String] = None): (Option[String], List[String])

}



abstract class MonoidQueue[M](val name: String, val monoid: Monoid[M], val serializer: Serializer[M]) extends MonoidQueueAux {
  type MA = M
}



