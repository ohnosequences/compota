//package ohnosequences.compota.aws.metamanager
//
//import ohnosequences.compota.AnyNispero
//import ohnosequences.compota.metamanager.AnyMetaManagerCommand
//
//sealed trait AwsCommand extends AnyMetaManagerCommand {
//  val component: String
//  val action: String
//  val args: List[String]
//
//  def prefix = {
//    component + "_" +
//      (if(action.isEmpty) "" else action + "_") +
//      (if(args.isEmpty) "" else args.reduce(_ + "_" + _))
//  }
//  def toCommand0 = Command0(component, action, args)
//}
//
//case class CreateWorkerGroup(nisperoIndex: Int) extends AwsCommand {
//  override val component: String = "worker_group"
//  override val action: String = "create"
//  override val args: List[String] = List(nisperoIndex.toString)
//}
//
//case object AddTasks extends AwsCommand {
//  override val component: String = "metamanager"
//  override val action: String = "add_tasks"
//  override val args: List[String] = List()
//}
//case class DeleteWorkerGroup(nisperoIndex: Int, reason: String, force: Boolean) extends AwsCommand {
//  override val component: String = "worker_group"
//  override val action: String = "delete"
//  override val args: List[String] = List(nisperoIndex.toString, reason, force.toString)
//}
//
//case class UnDeploy(reason: String, force: Boolean) extends AwsCommand {
//  override val component: String = "compota"
//  override val action: String = "undeploy"
//  override val args: List[String] = List[String](reason, force.toString)
//}
//
//case class ReduceQueue(index: Int, reason: String) extends AwsCommand {
//  override val component: String = "queue"
//  override val action: String = "reduce"
//  override val args: List[String] = List[String](index.toString, reason)
//}
//
//case class FinishCompota(reason: String, message: String) extends AwsCommand {
//  override val component: String = "compota"
//  override val action: String = "finish"
//  override val args: List[String] = List[String](reason, message)
//}
//
//case class UnDeployActions(reason: String, force: Boolean) extends AwsCommand {
//  override val component: String = "undeploy_actions"
//  override val action: String = "execute"
//  override val args: List[String] = List[String](reason, force.toString)
//}
//
//case object UnDeployMetaManger extends AwsCommand { //delete control queue as well
//  override val component: String = "metamanager"
//  override val action: String = "undeploy"
//  override val args: List[String] = List[String]()
//}
//
//
//case class DeleteQueue(index: Int, reason: String, force: Boolean) extends AwsCommand {
//  override val component: String = "queue"
//  override val action: String = "delete"
//  override val args: List[String] = List[String](index.toString, reason, force.toString)
//}
