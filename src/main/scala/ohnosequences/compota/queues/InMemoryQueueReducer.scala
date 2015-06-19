package ohnosequences.compota.queues

import java.util.concurrent.atomic.AtomicReference

import ohnosequences.compota.environment.Env

import scala.util.{Success, Try}

trait InMemoryReducible extends AnyMonoidQueue { monoidQueue =>

  val printEvery: Int = 100

  val result = new AtomicReference[Option[QueueElement]](None)
  def publish(env: Env, context: QueueContext, r: QueueElement): Try[Unit] = {
    Try{result.set(Some(r))}
  }
  override val reducer: AnyQueueReducer.of2[QueueElement, QueueContext] = new InMemoryQueueReducer(monoidQueue, printEvery, publish)
}

class InMemoryQueueReducer[I, Ctx](val queue: AnyQueue.of2m[I, Ctx], printEvery: Int, publish: (Env, Ctx, I) => Try[Unit])  extends AnyQueueReducer {

  override type Element = I

  override type QueueContext = Ctx

  override def reduce(env: Env, queueOp: AnyQueueOp.of2c[I, Ctx]): Try[Unit] = {
    //env.logger.info("calling fold")
    queueOp.foldLeftIndexed(queue.monoid.unit) { case (res, e, index) =>
      if ((index - 1) % printEvery == 0) {
        env.logger.debug("in memory reducer reduced: " + index + " elements from the queue: " + queue.name)
      }
      Try(queue.monoid.mult(res, e))
    }.flatMap { r =>
      env.logger.info("in memory reducer: queue " + queue.name + " reduced")
      publish(env, queueOp.context, r)
    }
  }

//  def publishResult(env: Env, res: I): Try[Unit] = {
//    env.logger.info("in memory reducer reduced: publishing result " + res.toString.take(200))
//    result.set(Some(res))
//    Success(())
//  }
}


