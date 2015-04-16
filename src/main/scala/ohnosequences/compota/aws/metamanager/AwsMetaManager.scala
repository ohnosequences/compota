package ohnosequences.compota.aws.metamanager

import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.{TerminationDaemon, AnyCompota}
import ohnosequences.compota.aws.{ErrorTable, AwsCompota, AnyAwsNispero, AwsEnvironment}
import ohnosequences.compota.local.LocalCompota
import ohnosequences.compota.metamanager.{UnDeploy, BaseMetaManagerCommand, BaseMetaManager, AnyMetaManager}
import ohnosequences.compota.queues.{AnyQueueOp, Queue}

import scala.util.{Success, Failure, Try}


class AwsMetaManager[U](val compota: AwsCompota[U]) extends BaseMetaManager {

  override type MetaManagerEnvironment = AwsEnvironment

  override type MetaManagerUnDeployingActionContext = U
  override type MetaManagerCompota = AwsCompota[U]

  override def process(command: BaseMetaManagerCommand, env: AwsEnvironment, unDeployContext: MetaManagerUnDeployingActionContext, controlQueueOp: AnyQueueOp, queueOps: List[AnyQueueOp],
                       terminationDaemon: TerminationDaemon[AwsEnvironment]): Try[List[BaseMetaManagerCommand]] = {
    command match {
      case UnDeploy(reason, force) if reason.startsWith(ErrorTable.errorTableError) => {
        env.errorTable.recover() match {
          case Failure(t) => {
            env.logger.error("couldn't recover error table")
            env.logger.error(t)
            Success(List(UnDeploy("couldn't recover error table", force = true)))
          }
          case Success(()) => {
            Success(List[BaseMetaManagerCommand]())
          }
        }
      }
      case c => super.process(c, env, unDeployContext, controlQueueOp, queueOps, terminationDaemon)
    }
  }
}
