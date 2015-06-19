package ohnosequences.compota.aws.queues

import java.util.concurrent.atomic.AtomicReference

import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.compota.environment.Env
import ohnosequences.compota.queues._

import scala.util.{Success, Try}

/**
 * Created by Evdokim on 19.06.2015.
 */



trait S3InMemoryReducible extends DynamoDBContextQueue with AnyMonoidQueue with AnySerializableQueue { monoidQueue =>
  
  val destination: Option[ObjectAddress]

  val printEvery: Int = 100

  val result = new AtomicReference[Option[QueueElement]](None)
  def publish(env: Env, context: QueueContext, r: QueueElement): Try[Unit] = destination match {
    case None => {
      env.logger.warn("S3InMemortReducible: destination for queue " + monoidQueue.name + " is not specified")
      Success(())
    }
    case Some(dst) => {
      env.logger.info("S3InMemortReducible: publishing results to " + dst)
      monoidQueue.serializer.toString(r).flatMap { s =>
        context.aws.s3.uploadString(dst, s)
      }
    }
  }
  override val reducer: AnyQueueReducer.of2[QueueElement, DynamoDBContext] = new InMemoryQueueReducer(monoidQueue, printEvery, publish)
}