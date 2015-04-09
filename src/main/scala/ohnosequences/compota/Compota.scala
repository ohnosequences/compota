package ohnosequences.compota

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.queues._
import ohnosequences.logging.ConsoleLogger
import scala.util.Try

trait AnyCompota {
  type CompotaEnvironment <: AnyEnvironment
  type Nispero <: AnyNispero.of[CompotaEnvironment]

  def launch(): Try[Unit]
}

abstract class Compota[E <: AnyEnvironment, N <: AnyNispero.of[E]](val nisperos: List[N], val reducers: List[AnyQueueReducer.of[E]])
  extends AnyCompota {

  type Nispero = N

  type CompotaEnvironment = E

  val nisperosNames: Map[String, Nispero] =  nisperos.map { nispero =>
    (nispero.name, nispero)
  }.toMap

  val nisperoGraph = new NisperoGraph(nisperosNames)

  def configurationChecks(): Boolean = {
    //sinks are leafs

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

  def createNispero(nispero: Nispero): Try[Unit]

  def deleteNispero(nispero: Nispero)

  def deleteQueue(queue: AnyQueueOp)

  def launchWorker(nispero: Nispero)

  def launchWorker(name: String) {

    nisperosNames.get(name) match {
      case None => {
        //report error
      }
      case Some(nispero) => launchWorker(nispero)
    }
  }

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

  def addTasks() //special compota defined

  def addTasks(environment: CompotaEnvironment): Try[Unit] //user defined

}
