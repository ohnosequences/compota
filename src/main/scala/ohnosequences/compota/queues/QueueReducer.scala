package ohnosequences.compota.queues

import ohnosequences.compota.environment.{Env, AnyEnvironment}
import ohnosequences.compota.queues.AnyQueueOp.of2c

import scala.util.{Success, Try}


trait AnyQueueReducer {
  type Element
  type QueueContext
  val queue: AnyQueue.of2[Element, QueueContext]
  def reduce(env: Env, queueOp: AnyQueueOp.of2c[Element, QueueContext]): Try[Unit]
}

class UnitQueueReducer[E, C](val queue: AnyQueue.of2[E, C]) extends AnyQueueReducer {
  override type Element = E
  override type QueueContext = C

  override def reduce(env: Env, queueOp: of2c[Element, QueueContext]): Try[Unit] = {
    env.logger.info("unit queue reducer: queue " + queue.name + " reduced")
    Success(())
  }
}

object AnyQueueReducer {
  type of[E] = AnyQueueReducer { type Element = E}
  type of2[E, Ctx] = AnyQueueReducer {
    type Element = E
    type QueueContext = Ctx
  }
}
//
//
//abstract class QueueReducer[E, Q <: AnyQueue.ofm[E]](val queue: Q) extends AnyQueueReducer {
//  override type Element = E
//}
