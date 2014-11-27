package ohnosequences.compota.local

import ohnosequences.compota.queues.MonoidQueueAux
import ohnosequences.compota.{Compota, NisperoAux}
import ohnosequences.compota.environment.ThreadEnvironment
import ohnosequences.compota.logging.ConsoleLogger

abstract class LocalCompota(nisperos: List[NisperoAux], sinks: List[MonoidQueueAux]) extends Compota(nisperos, sinks) {
  override def launchWorker(nispero: NisperoAux): Unit = {
    nispero.worker.start(new ThreadEnvironment)
  }

  override def launch(): Unit = {
    //thread pool???
  }
}
