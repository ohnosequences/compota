package ohnosequences.compota


import java.util.concurrent.atomic.AtomicReference

import ohnosequences.compota.console.AnyConsole
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.{QueueChecker, NisperoGraph}
import ohnosequences.compota.metamanager.AnyMetaManager
import ohnosequences.compota.queues._
import ohnosequences.logging.ConsoleLogger
import scala.util.{Failure, Success, Try}

trait AnyCompota {
  anyCompota =>

  type CompotaEnvironment <: AnyEnvironment[CompotaEnvironment]
  type CompotaNispero <: AnyNispero.of[CompotaEnvironment]

  type CompotaUnDeployActionContext

  val compotaUnDeployActionContext: AtomicReference[Option[CompotaUnDeployActionContext]] = new AtomicReference(None)

  type CompotaMetaManager <: AnyMetaManager.of[CompotaEnvironment]

  def metaManager: CompotaMetaManager

  def nisperos: List[CompotaNispero]

  def reducers: List[AnyQueueReducer.of[CompotaEnvironment]]

  type CompotaConfiguration <: AnyCompotaConfiguration

  def configuration: CompotaConfiguration

  def initialEnvironment: Try[CompotaEnvironment]

  def nisperosNames: Map[String, CompotaNispero] = nisperos.map { nispero =>
    (nispero.configuration.name, nispero)
  }.toMap

  def nisperoGraph: NisperoGraph[CompotaEnvironment] = {
    NisperoGraph(nisperosNames)
  }

  def launchMetaManager(): Try[CompotaEnvironment] = {
    initialEnvironment.flatMap { iEnv =>
      iEnv.subEnvironmentAsync("metamanager") { env =>
        metaManager.launchMetaManager(env)
      }
    }
  }

  def configurationChecks(env: CompotaEnvironment): Try[Boolean] = {
    Success(true)
  }


  def launchWorker(nispero: CompotaNispero): Try[CompotaEnvironment] = {
    initialEnvironment.flatMap { iEnv =>
      iEnv.subEnvironmentAsync("worker") { env =>
        nispero.worker.start(env)
      }
    }
  }

  def launchWorker(name: String): Try[CompotaEnvironment] = {
    nisperosNames.get(name) match {
      case None => {
        //report error
        Failure(new Error("nispero " + name + " doesn't exist"))
      }
      case Some(nispero) => launchWorker(nispero)
    }
  }

  def launch(): Try[CompotaEnvironment]

  def main(args: Array[String]): Unit = {
    val logger = new ConsoleLogger("compotaCLI", false, None)
    args.toList match {
      case "run" :: "worker" :: name :: Nil => launchWorker(name)
      case "run" :: "metamanager" :: Nil => launchMetaManager()
      case _ => logger.error("wrong command")
    }
  }

  def unDeployActions(env: CompotaEnvironment, context: CompotaUnDeployActionContext): Try[String]

  def forcedUnDeployActions(env: CompotaEnvironment): Try[String] = {
    Success("")
  }

  def finishUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit]

  def prepareUnDeployActions(env: CompotaEnvironment): Try[CompotaUnDeployActionContext]

  ///def sendUnDeployCommand(env: CompotaEnvironment, reason: String, force: Boolean): Try[Unit]

  //undeploy right now
  def forceUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit]

  def sendForceUnDeployCommand(env: CompotaEnvironment, reason: String, message: String): Try[Unit]

  //send command to metamanager that reduce queues etc
  def sendUnDeployCommand(env: CompotaEnvironment): Try[Unit]

  def addTasks(environment: CompotaEnvironment): Try[Unit]

  def startedTime(env: CompotaEnvironment): Try[Long]

  def compotaDeployed(env: CompotaEnvironment): Try[Boolean]

  def createNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit]

  def deleteNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit]

  def deleteManager(env: CompotaEnvironment): Try[Unit]

  def launchTerminationDaemon(queueChecker: QueueChecker[CompotaEnvironment], env: CompotaEnvironment): Try[TerminationDaemon[CompotaEnvironment]] = {
    env.logger.info("TERMINATION DAEMON IS HERE!")
    startedTime(env).flatMap { t =>
      val td = new TerminationDaemon[CompotaEnvironment](
        compota = anyCompota,
        queueChecker = queueChecker
      )
      td.start(env).map { u => td }

    }
  }

  def launchConsole(nisperoGraph: QueueChecker[CompotaEnvironment], controlQueue: AnyQueueOp, env: CompotaEnvironment): Try[AnyConsole]

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
  type ofE[E <: AnyEnvironment[E]] = AnyCompota { type CompotaEnvironment = E}

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
