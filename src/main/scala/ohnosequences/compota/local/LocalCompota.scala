package ohnosequences.compota.local

import ohnosequences.compota.queues.MonoidQueueAux
import ohnosequences.compota.{Compota, NisperoAux}
import ohnosequences.compota.environment.ThreadEnvironment
import ohnosequences.compota.logging.ConsoleLogger

abstract class LocalCompota(nisperos: List[LocalNisperoAux], sinks: List[MonoidQueueAux]) extends Compota[LocalNisperoAux](nisperos, sinks) {
  override def launchWorker(nispero: LocalNisperoAux): Unit = {
    nispero.worker.start(new ThreadEnvironment)
  }

  override def launch(): Unit = {
    //thread pool???
  }
}
