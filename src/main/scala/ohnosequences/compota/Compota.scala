package ohnosequences.compota

import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.logging.ConsoleLogger
import ohnosequences.compota.queues.{QueueOpAux, QueueAux, MonoidQueueAux, MonoidQueue}

import scala.util.Try

abstract class Compota[Nispero <: NisperoAux](nisperos: List[Nispero],  sinks: List[MonoidQueueAux]) {

  val logger = new ConsoleLogger()

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

  def deleteQueue(queue: QueueOpAux)


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
    val logger = new ConsoleLogger
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
