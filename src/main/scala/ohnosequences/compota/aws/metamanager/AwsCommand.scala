package ohnosequences.compota.aws.metamanager

import ohnosequences.compota.AnyNispero
import ohnosequences.compota.metamanager.AnyMetaManagerCommand

trait AwsCommand extends AnyMetaManagerCommand {

}

case class CreateWorkerGroup(nispero: AnyNispero) extends AwsCommand {
  override def prefix: String = "create_worker_" + nispero.name
}

case class UnDeploy(reason: String, force: Boolean) extends AwsCommand {
  override def prefix: String = "undeploy_" + reason + "_" + force
}

