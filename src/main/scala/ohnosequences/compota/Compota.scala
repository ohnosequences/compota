package ohnosequences.compota

import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.queues._
import ohnosequences.logging.ConsoleLogger

import scala.util.Try

trait AnyCompota {

  type Nispero <: AnyNispero
}

abstract class Compota[N <: AnyNispero, MQ <: AnyMonoidQueue](val nisperos: List[N], val sinks: List[MQ]) {

  type Nispero = N

  val logger = new ConsoleLogger("compota")

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

  def deleteQueue(queue: AnyQueueOps)


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
      case _ => logger.error("wrong command")
    }
  }

  def launch(): Unit = {
    if(configurationChecks()) {
      addTasks()
    }
  }

  def addTasks()


}
