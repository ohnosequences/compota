package ohnosequences.compota

import ohnosequences.compota.aws.AwsEnvironment
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.local.{LocalContext, LocalEnvironment, MonkeyAppearanceProbability, Monkey}
import ohnosequences.compota.monoid.Monoid
import ohnosequences.compota.queues.{QueueReducer, AnyQueueReducer, Queue}

import scala.util.Try

class InMemoryQueueReducer[E <: AnyEnvironment[E], I, C, Q <: Queue[I, C]](queue: Q, context: E => C, monoid: Monoid[I],
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


class InMemoryQueueReducerLocal[I, Q <: Queue[I, LocalContext]](queue: Q,
                                                                monoid: Monoid[I],
                                                                monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability())
  extends InMemoryQueueReducer[LocalEnvironment, I, LocalContext, Q](queue, {e: LocalEnvironment => e.localContext}, monoid, monkeyAppearanceProbability)

object InMemoryQueueReducer {
  def apply[I, Q <: Queue[I, LocalContext]](
                                                          queue: Q,
                                                          monoid: Monoid[I],
                                                          monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability()
                                                          ): InMemoryQueueReducer[LocalEnvironment, I, LocalContext, Q] =
  new InMemoryQueueReducer[LocalEnvironment, I, LocalContext, Q](queue, {e: LocalEnvironment => e.localContext}, monoid, monkeyAppearanceProbability)

  def create[I, Q <: Queue[I, DynamoDBContext]](
                                     queue: Q,
                                     monoid: Monoid[I],
                                     monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability()
                                     ): InMemoryQueueReducer[AwsEnvironment, I, DynamoDBContext, Q] =
    new InMemoryQueueReducer[AwsEnvironment, I, DynamoDBContext, Q](queue, {e: AwsEnvironment => e.createDynamoDBContext()}, monoid, monkeyAppearanceProbability)
}
