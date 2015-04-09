package ohnosequences.compota

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.local.{ThreadEnvironment, MonkeyAppearanceProbability, Monkey}
import ohnosequences.compota.monoid.Monoid
import ohnosequences.compota.queues.{QueueReducer, AnyQueueReducer, Queue}

import scala.util.Try

class InMemoryQueueReducer[E <: AnyEnvironment, I, C, Q <: Queue[I, C]](queue: Q, context: E => C, monoid: Monoid[I],
                                          val monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability())
  extends QueueReducer[E, I, C, Q](queue, context) {

  override def reduce(environment: E): Try[Unit] = {
    Monkey.call(
      {
        var res = monoid.unit
        queue.create(context(environment)).flatMap { queueOp =>
          queueOp.forEach { case (id, e) =>
            res = monoid.mult(res, e)
          }.map { r =>
            environment.logger.info("queue " + queue.name +  " reduced: " + res)
          }
        }
      }, monkeyAppearanceProbability.reducer
    )
  }
}

object InMemoryQueueReducer {
  def apply[I, Q <: Queue[I, Unit]](
                                                          queue: Q,
                                                          monoid: Monoid[I],
                                                          monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability()
                                                          ): InMemoryQueueReducer[ThreadEnvironment, I, Unit, Q] =
  new InMemoryQueueReducer[ThreadEnvironment, I, Unit, Q](queue, {e: ThreadEnvironment => Unit}, monoid, monkeyAppearanceProbability)
}
