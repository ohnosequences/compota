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
  type CompotaEnvironment <: AnyEnvironment[CompotaEnvironment]
  type CompotaNispero <: AnyNispero.of[CompotaEnvironment]
  type CompotaMetaManager <:  AnyMetaManager.of[CompotaEnvironment]

  type CompotaUnDeployActionContext

  def nisperos: List[CompotaNispero]
  def reducers: List[AnyQueueReducer.of[CompotaEnvironment]]


  type CompotaConfiguration <: AnyCompotaConfiguration
  def configuration: CompotaConfiguration

  def nisperosNames: Map[String, CompotaNispero] =  nisperos.map { nispero =>
    (nispero.configuration.name, nispero)
  }.toMap

  def launchMetaManager(): Try[CompotaEnvironment]

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

  def launchWorker(nispero: CompotaNispero): Try[CompotaEnvironment]

  def launchWorker(name: String): Try[CompotaEnvironment] = {

    nisperosNames.get(name) match {
      case None => {
        //report error
        Failure(new Error("nispero " + name + " doesn't exist"))
      }
      case Some(nispero) => launchWorker(nispero)
    }
  }

  def launch(): Try[Unit]

  def main(args: Array[String]): Unit = {
    val logger = new ConsoleLogger("compotaCLI", false, None)
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

  def createNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit]

  def deleteNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit]

  def deleteManager(env: CompotaEnvironment): Try[Unit]

  def launchTerminationDaemon(graph: NisperoGraph, env: CompotaEnvironment): Try[TerminationDaemon[CompotaEnvironment]] = {
    startedTime().flatMap { t =>
      val td = new TerminationDaemon[CompotaEnvironment](
        nisperoGraph = graph,
        sendUnDeployCommand = sendUnDeployCommand,
        startedTime = t,
        timeout = configuration.timeout,
        terminationDaemonIdleTime = configuration.terminationDaemonIdleTime
      )
      td.start(env).map{ u => td}

    }
  }

  def launchConsole(nisperoGraph: NisperoGraph, env: CompotaEnvironment): Try[AnyConsole]
}

object AnyCompota {
  type of2[E <: AnyEnvironment[E], U] = AnyCompota { type CompotaEnvironment = E ; type CompotaUnDeployActionContext = U  }
  type of[E <: AnyEnvironment[E], N <: AnyNispero.of[E]] = AnyCompota {
    type CompotaEnvironment = E ;
    type CompotaNispero = N  }
  type of3[E <: AnyEnvironment[E], U, N <: AnyNispero.of[E]] = AnyCompota {
    type CompotaEnvironment = E
    type CompotaUnDeployActionContext = U
    type CompotaNispero = N
  }

}

//abstract class Compota[E <: AnyEnvironment[E], N <: AnyNispero.of[E], U, C <: AnyCompotaConfiguration](
//                                                                    override val nisperos: List[N],
//                                                                    override val reducers: List[AnyQueueReducer.of[E]],
//                                                                    val configuration: C)
//  extends AnyCompota {
//
//  type Nispero = N
//  type CompotaEnvironment = E
//  type CompotaUnDeployActionContext = U
//  type CompotaConfiguration = C
//
//}
