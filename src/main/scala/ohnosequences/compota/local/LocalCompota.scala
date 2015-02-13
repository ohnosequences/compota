package ohnosequences.compota.local

import ohnosequences.compota.queues.{AnyMonoidQueue}
import ohnosequences.compota.{Compota, AnyNispero}
import ohnosequences.compota.environment.ThreadEnvironment
import ohnosequences.logging.ConsoleLogger

abstract class LocalCompota(nisperos: List[LocalNisperoAux], sinks: List[AnyMonoidQueue]) extends Compota[LocalNisperoAux](nisperos, sinks) {


  override def launchWorker(nispero: LocalNisperoAux): Unit = {

    object workerThread extends Thread("worker") {
      val env = new ThreadEnvironment(this)
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
