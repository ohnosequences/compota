package ohnosequences.compota.aws.metamanager

import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.aws.queues.{DynamoDBQueue, DynamoDBContext}
import ohnosequences.compota.console.AnyConsole
import ohnosequences.compota.graphs.QueueChecker
import ohnosequences.compota.queues.AnyQueue.of2
import ohnosequences.compota.{TerminationDaemon, AnyCompota}
import ohnosequences.compota.aws._
import ohnosequences.compota.local.LocalCompota
import ohnosequences.compota.metamanager._
import ohnosequences.compota.queues.{AnyQueueOp, Queue}

import scala.util.{Success, Failure, Try}


class AwsMetaManager[U](val compota: AnyAwsCompota.of[U]) extends BaseMetaManager {

  override type MetaManagerEnvironment = AwsEnvironment
  override type MetaManagerNispero = AnyAwsNispero
  override type MetaManagerUnDeployingActionContext = U
  override type MetaManagerControlQueueContext = DynamoDBContext
  override type MetaManagerCompota = AnyAwsCompota.of[U]


  override def controlQueueContext(env: MetaManagerEnvironment): MetaManagerControlQueueContext = env.createDynamoDBContext

  override val controlQueue = new DynamoDBQueue[BaseMetaManagerCommand]("control_queue", BaseCommandSerializer)

  override def process(command: BaseMetaManagerCommand,
                       ctx: AnyProcessContext.of[MetaManagerEnvironment, MetaManagerUnDeployingActionContext]
                      ): Try[List[BaseMetaManagerCommand]] = {
    command match {
//      case UnDeploy(reason, force) if reason.startsWith(AwsErrorTable.errorTableError) => {
//        env.errorTable.recover() match {
//          case Failure(t) => {
//            env.logger.error("couldn't recover error table")
//            env.logger.error(t)
//            Success(List(UnDeploy("couldn't recover error table", force = true)))
//          }
//          case Success(()) => {
//            Success(List[BaseMetaManagerCommand]())
//          }
//        }
//      }
      case c => super.process(c, ctx)
    }
  }


}
