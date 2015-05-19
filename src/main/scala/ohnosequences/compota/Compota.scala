package ohnosequences.compota


import ohnosequences.compota.Namespace._
import ohnosequences.compota.console.{UnfilteredConsoleServer, AnyConsole, Console}
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.metamanager.AnyMetaManager
import ohnosequences.compota.queues._
import ohnosequences.logging.ConsoleLogger
import scala.util.{Failure, Success, Try}

trait AnyCompota {
  type CompotaEnvironment <: AnyEnvironment
  type Nispero <: AnyNispero.of[CompotaEnvironment]
  type MetaManager <:  AnyMetaManager.of[CompotaEnvironment]

  type CompotaUnDeployActionContext

  val nisperos: List[Nispero]
  val reducers: List[AnyQueueReducer.of[CompotaEnvironment]]

  val configuration: AnyCompotaConfiguration

  def nisperosNames: Map[String, Nispero] =  nisperos.map { nispero =>
    (nispero.configuration.name, nispero)
  }.toMap

  def launchMetaManager(): Unit

  def configurationChecks(env: CompotaEnvironment): Try[Boolean] = {
    //sinks are leafs

    //reducers have different queues!!!!
    //one nispero for one input queue
    //    (nisperos.find(_.inputQueue.isInstanceOf[ProductQueue[_, _]]) match {
    //      case Some(nispero) => logger.info("nispero " + nispero.name + " uses product queues as an input"); false
    //      case None => true
    //    }) || {
    //      //etc
    //      true
    //    }
    Success(true)
  }

  def launchWorker(nispero: Nispero): Unit

  def launchWorker(name: String) {

    nisperosNames.get(name) match {
      case None => {
        //report error
      }
      case Some(nispero) => launchWorker(nispero)
    }
  }

  def launch(): Try[Unit]

  def main(args: Array[String]): Unit = {
    val logger = new ConsoleLogger("compotaCLI")
    args.toList match {
      case "run" :: "worker" :: name :: Nil => launchWorker(name)
      case "run" :: "metamanager" :: Nil => launchMetaManager()
      case _ => logger.error("wrong command")
    }
  }

  def unDeployActions(force: Boolean, env: CompotaEnvironment, context: CompotaUnDeployActionContext): Try[String]

  def finishUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit]

  def prepareUnDeployActions(env: CompotaEnvironment): Try[CompotaUnDeployActionContext]

  def sendUnDeployCommand(env: CompotaEnvironment, reason: String, force: Boolean): Try[Unit]

  def addTasks(environment: CompotaEnvironment): Try[Unit] //user defined

  def startedTime(): Try[Long]

  def tasksAdded(): Try[Boolean]

  def setTasksAdded(): Try[Unit]

  def createNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit]

  def deleteNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit]

  def deleteManager(env: CompotaEnvironment): Try[Unit]

  def launchTerminationDaemon(terminationDaemon: TerminationDaemon[CompotaEnvironment]): Try[Unit]


  def getConsoleInstance(nisperoGraph: NisperoGraph, env: CompotaEnvironment): AnyConsole

  def launchConsole(compota: AnyConsole, env: CompotaEnvironment): Unit
}

object AnyCompota {
  type of[E <: AnyEnvironment, U] = AnyCompota { type CompotaEnvironment = E ; type CompotaUnDeployActionContext = U  }
}

abstract class Compota[E <: AnyEnvironment, N <: AnyNispero.of[E], U](
                                                                    override val nisperos: List[N],
                                                                    override val reducers: List[AnyQueueReducer.of[E]],
                                                                    val configuration: AnyCompotaConfiguration)
  extends AnyCompota {

  type Nispero = N
  type CompotaEnvironment = E
  type CompotaUnDeployActionContext = U

}
