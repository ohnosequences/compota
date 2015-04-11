package ohnosequences.compota.aws.metamanager

import ohnosequences.compota.Compota
import ohnosequences.compota.aws.AwsCompota
import ohnosequences.compota.serialization.{JSON, Serializer}

import scala.util.{Failure, Try}


//todo find some ADT to Json library
class CommandSerializer(compota: AwsCompota) extends Serializer[AwsCommand] {
  override def fromString(s: String): Try[AwsCommand] = {
    JSON.extract[Command0](s).flatMap { command0 =>
      command0.toAwsCommand
    }
  }

  override def toString(t: AwsCommand): Try[String] = {
    JSON.toJSON(t.toCommand0)
  }
}

case class Command0(component: String, action: String, args: List[String]) { command0 =>

  def toAwsCommand: Try[AwsCommand] = {
    val createWorkerGroup = CreateWorkerGroup(1)
    val deleteWorkerGroup = DeleteWorkerGroup(1)
    val unDeploy = UnDeploy("reason", true)
    val unDeployActions = UnDeployActions(true)
    val reduceQueue = ReduceQueue(1)
    val deleteQueue = DeleteQueue(1)


    command0 match {
      case Command0(createWorkerGroup.component, createWorkerGroup.action, index :: Nil) => Try{ CreateWorkerGroup(index.toInt) }
      case Command0(deleteWorkerGroup.component, deleteWorkerGroup.action, index :: Nil) => Try{ DeleteWorkerGroup(index.toInt) }
      case Command0(unDeploy.component, unDeploy.action, reason :: force :: Nil) => Try{ UnDeploy(reason, force.toBoolean) }
      case Command0(reduceQueue.component, reduceQueue.action, index :: Nil) => Try{ ReduceQueue(index.toInt) }
      case Command0(PrepareUnDeployActions.component, PrepareUnDeployActions.action, Nil) => Try{ PrepareUnDeployActions }
      case Command0(unDeployActions.component, unDeployActions.action, force :: Nil) => Try{ UnDeployActions(force.toBoolean) }
      case Command0(CreateErrorTable.component, CreateErrorTable.action, Nil) => Try{ CreateErrorTable }
      case Command0(DeleteErrorTable.component, DeleteErrorTable.action, Nil) => Try{ DeleteErrorTable }
      //case Command0(CreateControlQueue.component, CreateControlQueue.action, Nil) => Try{ CreateControlQueue }
      case Command0(UnDeployMetaManger.component, UnDeployMetaManger.action, Nil) => Try{ UnDeployMetaManger }
      case Command0(FinishCompota.component, FinishCompota.action, Nil) => Try{ FinishCompota }
      case Command0(reduceQueue.component, reduceQueue.action, index :: Nil) => Try{ ReduceQueue(index.toInt) }
      case _ => Failure(new Error("can't parse AwsCommand encoded with " + command0))
    }
  }

}




