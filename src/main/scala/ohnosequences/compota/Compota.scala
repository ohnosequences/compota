package ohnosequences.compota


import java.util.concurrent.atomic.AtomicReference

import ohnosequences.compota.console.AnyConsole
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.{QueueChecker, NisperoGraph}
import ohnosequences.compota.metamanager.AnyMetaManager
import ohnosequences.compota.queues._
import ohnosequences.logging.{Logger, ConsoleLogger}
import scala.util.{Failure, Success, Try}

trait AnyCompota {
  anyCompota =>

  type CompotaEnvironment <: AnyEnvironment[CompotaEnvironment]
  type CompotaNispero <: AnyNispero.of[CompotaEnvironment]

  type CompotaUnDeployActionContext

  type CompotaMetaManager <: AnyMetaManager.of[CompotaEnvironment]

  def metaManager: CompotaMetaManager

  def nisperos: List[CompotaNispero]

  type CompotaConfiguration <: AnyCompotaConfiguration

  def configuration: CompotaConfiguration

  //for metamanager and workers
  def initialEnvironment: Try[CompotaEnvironment]

  //to start compota
  def localEnvironment(cliLogger: ConsoleLogger, args: List[String]): Try[CompotaEnvironment]

  def nisperosNames: Map[String, CompotaNispero] = nisperos.map { nispero =>
    (nispero.configuration.name, nispero)
  }.toMap

  def nisperoGraph: NisperoGraph[CompotaEnvironment] = {
    NisperoGraph(nisperosNames)
  }

  def configurationChecks(env: CompotaEnvironment): Try[Boolean] = {
    env.logger.info("checking nispero graph")
    nisperoGraph.graph.sort().map { c =>
      true
    }
  }

  def launch(env: CompotaEnvironment): Try[CompotaEnvironment]

  def main(args: Array[String]): Unit = {
    val logger = new ConsoleLogger("compotaCLI", false)
    args.toList match {
      case "run" :: "worker" :: name :: Nil => {
        initialEnvironment.map { env =>
          env.logger.info("starting worker for nispero: " + name)
          nisperosNames.get(name) match {
            case None => {
              env.logger.error(new Error("nispero " + name + " doesn't exist"))
            }
            case Some(nispero) => {
              nispero.worker.start(env)
            }
          }
        }.recoverWith { case t =>
          println("couldn't initializate initial environment")
          Failure(t)
        }
      }
      case "checks" :: Nil => {
        localEnvironment(logger, List[String]()).flatMap { env =>
          configurationChecks(env).map { r =>
            env.logger.info("configuration checked")
          }
        }.recoverWith { case t =>
          logger.info("configuration checks failed")
          logger.error(t)
          Failure(t)
        }
      }
      case "run" :: "manager" :: "manager" :: Nil => {
        initialEnvironment.map { env =>
          metaManager.launchMetaManager(env)
        }.recoverWith { case t =>
          println("couldn't initializate initial environment")
          Failure(t)
        }
      }
      case "start" :: Nil => {
        localEnvironment(logger, List[String]()).flatMap { env =>
          configurationChecks(env).flatMap { r =>
            env.logger.info("configuration checked")
            launch(env)
          }
        }.recoverWith { case t =>
          logger.error(new Error("configuration checks failed", t))
          t.printStackTrace()
          Failure(t)
        }
      }
      case "undeploy" :: Nil => {
        localEnvironment(logger, List[String]()).flatMap { env =>
          sendForceUnDeployCommand(env, "manual termination from CLI", "manual termination from CLI")
        }.recoverWith { case t =>
          t.printStackTrace()
          Failure(t)
        }
      }
      case "undeploy" :: "force" :: Nil => {
        localEnvironment(logger, List[String]()).flatMap { env =>
          forceUnDeploy(env, "manual termination from CLI", "manual termination from CLI")
        }.recoverWith { case t =>
          t.printStackTrace()
          Failure(t)
        }
      }
      case _ => logger.error("wrong command")
    }
  }

  def unDeployActions(env: CompotaEnvironment, context: CompotaUnDeployActionContext): Try[String]

  def forcedUnDeployActions(env: CompotaEnvironment): Try[String] = {
    Success("")
  }

  def sendNotification(env: CompotaEnvironment, subject: String, message: String): Try[Unit]

  def finishUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit]

  def prepareUnDeployActions(env: CompotaEnvironment): Try[CompotaUnDeployActionContext]

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
    startedTime(env).flatMap { t =>
      val td = new TerminationDaemon[CompotaEnvironment](
        compota = anyCompota,
        queueChecker = queueChecker
      )
      td.start(env).map { u => td }

    }
  }

  def launchConsole(nisperoGraph: QueueChecker[CompotaEnvironment], controlQueue: AnyQueueOp, env: CompotaEnvironment): Try[AnyConsole]

  def customMessage: String = ""

}

object AnyCompota {
  type of2[E <: AnyEnvironment[E], U] = AnyCompota {type CompotaEnvironment = E; type CompotaUnDeployActionContext = U}
  type of[E <: AnyEnvironment[E], N <: AnyNispero.of[E]] = AnyCompota {
    type CompotaEnvironment = E
    type CompotaNispero = N}
  type of3[E <: AnyEnvironment[E], U, N <: AnyNispero.of[E]] = AnyCompota {
    type CompotaEnvironment = E
    type CompotaUnDeployActionContext = U
    type CompotaNispero = N
  }
  type ofE[E <: AnyEnvironment[E]] = AnyCompota {type CompotaEnvironment = E}

}

