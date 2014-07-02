package ohnosequences.nisperon.queues

import ohnosequences.nisperon.{unitSerializer, unitMonoid}


object unitMessage extends Message[Unit] {
  val id: String = "unit"

  def value() = ()

  def delete() {}

  def changeMessageVisibility(secs: Int) {}
}

object unitQueue extends MonoidQueue[Unit]("unit", unitMonoid, unitSerializer) {

  def initRead() {}
  def initWrite() {}

  def put(parentId: String, nispero: String, values: List[Unit]) {}

  def read() = unitMessage

  def reset() {}

  def isEmpty = true

  def list(): List[String] = List()

  def read(id: String) = None

  def delete(id: String) {}

  def delete() {}

  def sqsQueueInfo(): Option[SQSQueueInfo] = {
    None
  }

//  override type Listening = Unit
//
//  override def listNextChunk(limit: Int, listening: Listening): (Listening, List[String]) = ((), List[String]())
//
//  override def listChunk(limit: Int): (Listening, List[String]) = ((), List[String]())

  override def listChunk(limit: Int, lastKey: Option[String]): (Option[String], List[String]) = (None, List[String]())
}
