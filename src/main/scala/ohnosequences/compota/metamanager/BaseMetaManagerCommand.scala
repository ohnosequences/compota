package ohnosequences.compota.metamanager

import ohnosequences.compota.serialization.{JSON, Serializer}

import scala.util.{Failure, Try}

object BaseCommandSerializer extends Serializer[BaseMetaManagerCommand] {
  override def fromString(s: String): Try[BaseMetaManagerCommand] = {
    JSON.extract[Command0](s).flatMap { command0 =>
      command0.toBaseMetaManagerCommand
    }
  }

  override def toString(t: BaseMetaManagerCommand): Try[String] = {
    JSON.toJSON(t.toCommand0)
  }
}

case class Command0(component: String, action: String, args: List[String]) { command0 =>

  def toBaseMetaManagerCommand: Try[BaseMetaManagerCommand] = {
    val createNisperoWorkers = CreateNisperoWorkers(1)
    val deleteNisperoWorkers = DeleteNisperoWorkers(1)
    val forceUndeploy = ForceUnDeploy("reason", "message")
    //  val deleteErrorTable = DeleteErrorTable("reason", true)
    val reduceQueue = ReduceQueue(1)
    val deleteQueue = DeleteQueue(1)
    val prepareUnDeployActions = PrepareUnDeployActions(true)
    val finishCompota = FinishCompota("reason", "message")
    val sendNotification = FinishCompota("subject", "message")
    val unDeployMetaManger = UnDeployMetaManger("reason", "message")



    command0 match {
      case Command0(createNisperoWorkers.component, createNisperoWorkers.action, index :: Nil) => Try{ CreateNisperoWorkers(index.toInt) }
      case Command0(deleteNisperoWorkers.component, deleteNisperoWorkers.action, index :: Nil) => Try{ DeleteNisperoWorkers(index.toInt) }
      case Command0(deleteQueue.component, deleteQueue.action, index :: Nil) => Try{ DeleteQueue(index.toInt) }
      case Command0(UnDeploy.component, UnDeploy.action, Nil) => Try{ UnDeploy }
      case Command0(reduceQueue.component, reduceQueue.action, index :: Nil) => Try{ ReduceQueue(index.toInt) }
      case Command0(forceUndeploy.component, forceUndeploy.action, reason :: message :: Nil) => Try{ ForceUnDeploy(reason, message) }
      case Command0(unDeployMetaManger.component, unDeployMetaManger.action, reason :: message :: Nil) => Try{ UnDeployMetaManger(reason, message)}
      case Command0(AddTasks.component, AddTasks.action, Nil) => Try{ AddTasks }
      case Command0(LaunchTerminationDaemon.component, LaunchTerminationDaemon.action, Nil) => Try{ LaunchTerminationDaemon }
      case Command0(LaunchConsole.component, LaunchConsole.action, Nil) => Try{ LaunchConsole }
      case Command0(prepareUnDeployActions.component, prepareUnDeployActions.action, execute :: Nil) => Try{ PrepareUnDeployActions(execute.toBoolean) }
      case Command0(ExecuteUnDeployActions.component, ExecuteUnDeployActions.action, Nil) => Try{ ExecuteUnDeployActions }
      case Command0(sendNotification.component, sendNotification.action, subject :: message :: Nil) => Try{ SendNotification(subject, message) }
      case Command0(finishCompota.component, finishCompota.action, reason :: message :: Nil) => Try{ FinishCompota(reason, message) }
      case _ => Failure(new Error("couldn't parse BaseManagerCommand encoded with " + command0))
    }
  }
}



sealed trait BaseMetaManagerCommand extends AnyMetaManagerCommand {
  val component: String
  val action: String
  val args: List[String]

  def prefix: String = {
    component + "_" +
      (if(action.isEmpty) "" else action) +
      (if(args.isEmpty) "" else ":_" + args.reduce(_ + "_" + _))
  }
  def toCommand0 = Command0(component, action, args)

  def printMessage(message: String): String = {
    message.split(System.lineSeparator()).toList match {
      case line1 :: line2 :: tail => {
        if (line1.length > 50) {
          line1.take(50) + "..."
        } else {
          line1
        }
      }
      case _ => {
        if (message.length > 50) {
          message.take(50) + "..."
        } else {
          message
        }
      }
    }
  }

  override def toString = printMessage(prefix)
}

case class SendNotification(subject: String, message: String) extends BaseMetaManagerCommand {
  override val component: String = "notification"
  override val action: String = "send"
  override val args: List[String] = List[String](subject, message)
}

case class CreateNisperoWorkers(nisperoIndex: Int) extends BaseMetaManagerCommand {
  override val component: String = "nispero"
  override val action: String = "create"
  override val args: List[String] = List(nisperoIndex.toString)
}

case object AddTasks extends BaseMetaManagerCommand {
  override val component: String = "metamanager"
  override val action: String = "add_tasks"
  override val args: List[String] = List()
}

case object LaunchTerminationDaemon extends BaseMetaManagerCommand {
  override val component: String = "termination_daemon"
  override val action: String = "start"
  override val args: List[String] = List()
}

case object LaunchConsole extends BaseMetaManagerCommand {
  override val component: String = "console"
  override val action: String = "start"
  override val args: List[String] = List()
}

case class DeleteNisperoWorkers(nisperoIndex: Int) extends BaseMetaManagerCommand {
  override val component: String = "nispero"
  override val action: String = "delete"
  override val args: List[String] = List(nisperoIndex.toString)
}

case object UnDeploy extends BaseMetaManagerCommand {
  override val component: String = "compota"
  override val action: String = "undeploy"
  override val args: List[String] = List[String]()
}

case class ReduceQueue(index: Int) extends BaseMetaManagerCommand {
  override val component: String = "queue"
  override val action: String = "reduce"
  override val args: List[String] = List[String](index.toString)
}

case class FinishCompota(reason: String, message: String) extends BaseMetaManagerCommand {
  override val component: String = "compota"
  override val action: String = "finish"
  override val args: List[String] = List[String](reason, message)
}

case class PrepareUnDeployActions(executeUndeployActions: Boolean) extends BaseMetaManagerCommand {
  override val component: String = "undeploy_actions"
  override val action: String = "prepare"
  override val args: List[String] = List[String](executeUndeployActions.toString)
}

case object ExecuteUnDeployActions extends BaseMetaManagerCommand {
  override val component: String = "undeploy_actions"
  override val action: String = "execute"
  override val args: List[String] = List[String]()
}

case class ForceUnDeploy(reason: String, message: String) extends BaseMetaManagerCommand {
  override val component: String = "compota"
  override val action: String = "force_undeploy"
  override val args: List[String] = List[String](reason, message)
}

case class UnDeployMetaManger(reason: String, message: String) extends BaseMetaManagerCommand {
override val component: String = "metamanager"
  override val action: String = "undeploy"
  override val args: List[String] = List[String](reason, message)
}

case class DeleteQueue(index: Int) extends BaseMetaManagerCommand {
  override val component: String = "queue"
  override val action: String = "delete"
  override val args: List[String] = List[String](index.toString)
}
