package ohnosequences.compota

import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.logging.ConsoleLogger
import ohnosequences.compota.queues.{MonoidQueueAux, MonoidQueue}

abstract class Compota[NisperoType <: NisperoAux](nisperos: List[NisperoType],  sinks: List[MonoidQueueAux]) {

  val nisperosNames: Map[String, NisperoType] =  nisperos.map { nispero =>
    (nispero.name, nispero)
  }.toMap

  val nisperoGraph = new NisperoGraph(nisperosNames)


  def launchWorker(nispero: NisperoType)

  def launchWorker(name: String) {
    addTasks()
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

  def launch()

  def addTasks()


}
