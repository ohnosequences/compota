package ohnosequences.compota.local

import ohnosequences.compota.queues.{AnyMonoidQueue}
import ohnosequences.compota.{Compota, AnyNispero}
import ohnosequences.compota.environment.ThreadEnvironment
import ohnosequences.logging.ConsoleLogger

abstract class LocalCompota(nisperos: List[LocalNisperoAux], sinks: List[AnyMonoidQueue]) extends Compota[LocalNisperoAux](nisperos, sinks) {
  override def launchWorker(nispero: LocalNisperoAux): Unit = {
    nispero.createWorker().start(new ThreadEnvironment)
  }

  override def launch(): Unit = {
    //thread pool???
  }
}
