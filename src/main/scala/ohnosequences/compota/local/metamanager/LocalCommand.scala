//package ohnosequences.compota.local.metamanager
//
//import ohnosequences.compota.metamanager.AnyMetaManagerCommand
//
//
//trait LocalCommand extends AnyMetaManagerCommand
//
//case class StartNispero(index: Int) extends LocalCommand {
//  override def prefix: String = "start_nispero_" + index
//}
//
//case object AddTasks extends LocalCommand {
//  override def prefix: String = "add_tasks"
//}
//
//case class UnDeploy(reason: String, force: Boolean) extends LocalCommand {
//  override def prefix: String = "undeploy_" + reason + "_" + force
//}
//
//case class StopNispero(nisperoId: Int, force: Boolean) extends LocalCommand {
//  override def prefix: String = "stop_nispero_" + nisperoId + "_" + force
//}
//
//case class LaunchReducer(reducerId: Int) extends LocalCommand {
//  override def prefix: String = "launch_reducer_" + reducerId
//}
//
//case class UnDeployActions(force: Boolean) extends LocalCommand {
//  override def prefix: String = "undeploy_actions_" + force
//}
//case class FinishUndeploy(message: String) extends LocalCommand {
//  override def prefix: String = "finish_undeploy"
//}//send e-mails etc
