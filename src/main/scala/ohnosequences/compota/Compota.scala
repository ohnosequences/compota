package ohnosequences.compota

import ohnosequences.compota.enviroment.ThreadInstance
import ohnosequences.compota.logging.ConsoleLogger

abstract class Compota(nisperos: List[NisperoAux]) {
  val nisperosNames: Map[String, NisperoAux] =  nisperos.map { nispero =>
    (nispero.name, nispero)
  }.toMap

  def launchWorker(name: String) {
    addTasks()
    nisperosNames.get(name) match {
      case None => {
        //report error
      }
      case Some(nispero) => nispero.worker.start(new ThreadInstance)
    }
  }

  def main(args: Array[String]): Unit = {
    val logger = new ConsoleLogger
    args.toList match {
      case "run" :: "worker" :: name :: Nil => launchWorker(name)
      case _ => logger.error("wrong command")
    }
  }


  def addTasks()
}
