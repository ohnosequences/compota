package ohnosequences.compota.queues

import ohnosequences.compota.environment.{Env, AnyEnvironment}

import scala.util.Try


trait AnyQueueReducer {
  type Element
  def reduce(env: Env, queue: AnyQueue.ofm[Element], queueOp: AnyQueueOp.of1[Element]): Try[Unit]
}

object AnyQueueReducer {
  type of[E] = AnyQueueReducer { type Element = E}
}


abstract class QueueReducer[E, Q <: AnyQueue.ofm[E]](val queue: Q) extends AnyQueueReducer {
  override type Element = E
}
