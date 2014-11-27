package ohnosequences.compota.aws

import ohnosequences.compota.queues.MonoidQueueAux
import ohnosequences.compota.{Compota, NisperoAux}

abstract class AwsCompota(nisperos: List[AwsNisperoAux], sinks: List[MonoidQueueAux]) extends Compota[AwsNisperoAux](nisperos, sinks) {
  override def launchWorker(nispero: AwsNisperoAux): Unit = {
    println(nispero.awsConfiguration)
  }

  override def launch(): Unit = {
    println("launching metamanager")
  }

}
