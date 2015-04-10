package ohnosequences.compota

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.metamanager.AnyMetaManager
import ohnosequences.compota.queues._
import ohnosequences.logging.ConsoleLogger
import scala.util.{Success, Try}

trait AnyCompota {
  type CompotaEnvironment <: AnyEnvironment
  type Nispero <: AnyNispero.of[CompotaEnvironment]
  type MetaManager <:  AnyMetaManager.of[CompotaEnvironment]

  val nisperos: List[Nispero]
  val reducers: List[AnyQueueReducer.of[CompotaEnvironment]]

  val nisperosNames: Map[String, Nispero] =  nisperos.map { nispero =>
    (nispero.name, nispero)
  }.toMap

  def configurationChecks(): Boolean = {
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
    true
  }

  def launchWorker(nispero: Nispero)

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
      case "add" :: "tasks" :: Nil => {
        addTasks()
      }
      case _ => logger.error("wrong command")
    }
  }

  def unDeployActions(force: Boolean, env: CompotaEnvironment): Try[Unit]

  def finishUnDeploy(): Try[Unit]

  def prepareUnDeployActions(env: CompotaEnvironment): Try[Unit] = {Success(())}

  def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit]

  def addTasks()

  def addTasks(environment: CompotaEnvironment): Try[Unit] //user defined

  def launchTerminationDaemon(environment: CompotaEnvironment): Try[Unit] = {
    NisperoGraph[CompotaEnvironment](environment, nisperos).flatMap { nisperoGraph =>
      val terminationDaemon = new TerminationDaemon(nisperoGraph, sendUnDeployCommand)
      terminationDaemon.start(environment)
    }
  }

}

abstract class Compota[E <: AnyEnvironment, N <: AnyNispero.of[E]](
                                                                    override val nisperos: List[N],
                                                                    override val reducers: List[AnyQueueReducer.of[E]])
  extends AnyCompota {

  type Nispero = N
  type CompotaEnvironment = E

}
