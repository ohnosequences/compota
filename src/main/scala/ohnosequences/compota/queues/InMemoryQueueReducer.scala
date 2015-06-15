package ohnosequences.compota.queues

import java.util.concurrent.atomic.AtomicReference

import ohnosequences.compota.aws.AwsEnvironment
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.local.{LocalContext, LocalEnvironment, Monkey, MonkeyAppearanceProbability}
import ohnosequences.compota.monoid.Monoid

import scala.util.{Success, Try}

class InMemoryQueueReducer[E <: AnyEnvironment[E], I, C, Q <: Queue[I, C]](queue: Q, context: E => C, monoid: Monoid[I],
                                                                           val monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability())
  extends QueueReducer[E, I, C, Q](queue, context) {

  override def reduce(environment: E): Try[Unit] = {
    Monkey.call(
    {
      queue.create(context(environment)).flatMap { queueOp =>
        queueOp.foldLeft(monoid.unit) { case (res, e) =>
          Try(monoid.mult(res, e))
        }.flatMap { r =>
          environment.logger.info("queue " + queue.name + " reduced: " + r)
          publishResult(r)
        }
      }
    }, monkeyAppearanceProbability.reducer
    )
  }

  def publishResult(res: I): Try[Unit] = {
    Success(())
  }
}


class InMemoryQueueReducerLocal[I, Q <: Queue[I, LocalContext]](queue: Q,
                                                                monoid: Monoid[I],
                                                                result: AtomicReference[I],
                                                                monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability())
  extends InMemoryQueueReducer[LocalEnvironment, I, LocalContext, Q](queue, { e: LocalEnvironment => e.localContext }, monoid, monkeyAppearanceProbability) {
  override def publishResult(res: I): Try[Unit] = {
    Try {
      result.set(res)
    }
  }
}

object InMemoryQueueReducer {
  def apply[I, Q <: Queue[I, LocalContext]](
                                             queue: Q,
                                             monoid: Monoid[I],
                                             monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability()
                                             ): InMemoryQueueReducer[LocalEnvironment, I, LocalContext, Q] =
    new InMemoryQueueReducer[LocalEnvironment, I, LocalContext, Q](queue, { e: LocalEnvironment => e.localContext }, monoid, monkeyAppearanceProbability)

  def create[I, Q <: Queue[I, DynamoDBContext]](
                                                 queue: Q,
                                                 monoid: Monoid[I],
                                                 monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability()
                                                 ): InMemoryQueueReducer[AwsEnvironment, I, DynamoDBContext, Q] =
    new InMemoryQueueReducer[AwsEnvironment, I, DynamoDBContext, Q](queue, { e: AwsEnvironment => e.createDynamoDBContext }, monoid, monkeyAppearanceProbability)
}
