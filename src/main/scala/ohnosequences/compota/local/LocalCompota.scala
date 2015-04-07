package ohnosequences.compota.local

import ohnosequences.compota.queues.{AnyMonoidQueue}
import ohnosequences.compota.{Compota}
import ohnosequences.logging.{FileLogger, ConsoleLogger}
import java.io.File

abstract class LocalCompota(nisperos: List[LocalNisperoAux], sinks: List[AnyMonoidQueue], configuration: LocalCompotaConfiguration) extends Compota[LocalNisperoAux](nisperos, sinks) {


  override def launchWorker(nispero: LocalNisperoAux): Unit = {


    val workerPrefix = "worker_" + nispero.name

    val envLogger = new FileLogger(workerPrefix, new File(workerPrefix), debug = configuration.loggerDebug, printToConsole = true)
    object workerThread extends Thread(workerPrefix) {
      val env = new ThreadEnvironment(this, envLogger)
      override def run(): Unit = {
        nispero.createWorker().start(env)
      }
    }
    workerThread.start()
  }

  override def launch(): Unit = {
    //thread pool???
  }
}
