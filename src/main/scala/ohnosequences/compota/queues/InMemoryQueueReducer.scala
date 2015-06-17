package ohnosequences.compota.queues

import java.util.concurrent.atomic.AtomicReference

import ohnosequences.compota.environment.Env

import scala.util.{Success, Try}

trait InMemoryReducible extends AnyMonoidQueue {

  val result = new AtomicReference[Option[QueueElement]](None)

  val printEvery: Int = 100

  override def reducer: Option[AnyQueueReducer.of[QueueElement]] = Some(new InMemoryQueueReducer[QueueElement](printEvery, result))

}

class InMemoryQueueReducer[I](printEvery: Int, result: AtomicReference[Option[I]])  extends AnyQueueReducer {

  override type Element = I

  override def reduce(env: Env, queue: AnyQueue.ofm[I], queueOp: AnyQueueOp.of1[I]): Try[Unit] = {
    //env.logger.info("calling fold")
    queueOp.foldLeftIndexed(queue.monoid.unit) { case (res, e, index) =>
      if ((index - 1) % printEvery == 0) {
        env.logger.debug("in memory reducer reduced: " + index + " elements from the queue: " + queue.name)
      }
      Try(queue.monoid.mult(res, e))
    }.flatMap { r =>
      env.logger.info("in memory reducer: queue " + queue.name + " reduced")
      publishResult(env, r)
    }
  }

  def publishResult(env: Env, res: I): Try[Unit] = {
    env.logger.info("in memory reducer reduced: publishing result " + res.toString.take(200))
    result.set(Some(res))
    Success(())
  }
}


