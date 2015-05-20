package ohnosequences.compota.queues

import ohnosequences.compota.environment.{AnyEnvironment}

import scala.util.Try


trait AnyQueueReducer {
  type Environment <: AnyEnvironment[Environment]

  type QueueContext

  type QQ <: AnyQueue { type Context = QueueContext }
  val queue: QQ

  val context: Environment => QueueContext

  def reduce(environment: Environment): Try[Unit]
}

object AnyQueueReducer {
  type of[E <: AnyEnvironment[E]] = AnyQueueReducer { type Environment = E}
}


abstract class QueueReducer[Env <: AnyEnvironment[Env], In, Ctx, Q <: Queue[In, Ctx]](val queue: Q, val context:  Env => Ctx) extends AnyQueueReducer {
  type Environment = Env
  type QueueContext = Ctx
  type QQ = Q

}
