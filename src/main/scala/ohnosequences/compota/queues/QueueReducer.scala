package ohnosequences.compota.queues

import ohnosequences.compota.environment.Environment

import scala.util.Try


trait AnyQueueReducer {
  type CCtx
  
  type QQ <: AnyQueue
  val queue: QQ

  def reduce(environment: Environment[CCtx]): Try[Unit]
}

abstract class QueueReducer[In, Ctx, Q <: Queue[In, Ctx]](val queue: Q) extends AnyQueueReducer {
  type QQ = Q
  type CCtx = Ctx
}
