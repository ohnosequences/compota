package ohnosequences.compota.local

import ohnosequences.compota.environment.Environment
import ohnosequences.compota.queues.{Queue, QueueReducer, AnyQueue}
import ohnosequences.logging.Logger

import scala.util.Try


class LocalQueueReducer[In, Ctx, Q <: Queue[In, Ctx]](queue: Q) extends QueueReducer(queue) {
  override def reduce(environment: Environment[CCtx]): Try[Unit] = {
    queue.create(environment.queueCtx).flatMap { queueOp =>
      queueOp.
    }

  }
}
