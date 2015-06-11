//package ohnosequences.compota.aws.metamanager
//
//import ohnosequences.compota.Compota
//import ohnosequences.compota.aws.AwsCompota
//import ohnosequences.compota.serialization.{JSON, Serializer}
//
//import scala.util.{Failure, Try}
//
//
////todo find some ADT to Json library
//object CommandSerializer extends Serializer[AwsCommand] {
//  override def fromString(s: String): Try[AwsCommand] = {
//    JSON.extract[Command0](s).flatMap { command0 =>
//      command0.toAwsCommand
//    }
//  }
//
//  override def toString(t: AwsCommand): Try[String] = {
//    JSON.toJSON(t.toCommand0)
//  }
//}
//
//case class Command0(component: String, action: String, args: List[String]) { command0 =>
//
//  def toAwsCommand: Try[AwsCommand] = {
//    val createWorkerGroup = CreateWorkerGroup(1)
//    val deleteWorkerGroup = DeleteWorkerGroup(1, "reason", true)
//    val unDeploy = UnDeploy("reason", true)
//    val unDeployActions = UnDeployActions("reason", true)
//  //  val deleteErrorTable = DeleteErrorTable("reason", true)
//    val reduceQueue = ReduceQueue(1, "reason")
//    val deleteQueue = DeleteQueue(1, "reason", true)
//    val finishCompota = FinishCompota("reason", "message")
//
//
//
//    command0 match {
//      case Command0(createWorkerGroup.component, createWorkerGroup.action, index :: Nil) => Try{ CreateWorkerGroup(index.toInt) }
//      case Command0(deleteWorkerGroup.component, deleteWorkerGroup.action, index :: reason :: force :: Nil) => Try{ DeleteWorkerGroup(index.toInt, reason, force.toBoolean) }
//      case Command0(deleteQueue.component, deleteQueue.action, index :: reason :: force :: Nil) => Try{ DeleteQueue(index.toInt, reason, force.toBoolean) }
//      case Command0(unDeploy.component, unDeploy.action, reason :: force :: Nil) => Try{ UnDeploy(reason, force.toBoolean) }
//      case Command0(reduceQueue.component, reduceQueue.action, index :: reason :: Nil) => Try{ ReduceQueue(index.toInt, reason) }
//      //case Command0(PrepareUnDeployActions.component, PrepareUnDeployActions.action, Nil) => Try{ PrepareUnDeployActions }
//      case Command0(unDeployActions.component, unDeployActions.action, reason :: force :: Nil) => Try{ UnDeployActions(reason, force.toBoolean) }
//     // case Command0(CreateErrorTable.component, CreateErrorTable.action, Nil) => Try{ CreateErrorTable }
//      //case Command0(CreateControlQueue.component, CreateControlQueue.action, Nil) => Try{ CreateControlQueue }
//      case Command0(UnDeployMetaManger.component, UnDeployMetaManger.action, Nil) => Try{ UnDeployMetaManger }
//      case Command0(AddTasks.component, AddTasks.action, Nil) => Try{ AddTasks }
//
//      case Command0(finishCompota.component, finishCompota.action, reason :: message :: Nil) => Try{ FinishCompota(reason, message) }
//      case _ => Failure(new Error("can't parse AwsCommand encoded with " + command0))
//    }
//  }
//
//}
//
//
//
//
