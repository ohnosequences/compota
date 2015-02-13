package ohnosequences.compota.aws

import java.io.File

import ohnosequences.compota.queues._
import ohnosequences.compota.{Compota}
//import ohnosequences.nisperon.AWS

import scala.util.Try

abstract class AwsCompota(nisperos: List[AwsNisperoAux], sinks: List[AnyMonoidQueue], configuration: AwsCompotaConfigurationAux) extends Compota[AwsNisperoAux](nisperos, sinks) {


 // val aws = new AWS(new File("."))


  override def createNispero(nispero: AwsNisperoAux): Try[Unit] = Try {
    //aws.as.createAutoScalingGroup(nispero.configuration.workerAutoScalingGroup)

  }

  override def deleteNispero(nispero: AwsNisperoAux) = Try {
    //nispero.
  }

  override def deleteQueue(queue: AnyQueueOp) = Try {
    queue.delete
  }

  override def launchWorker(nispero: AwsNisperoAux): Unit = {

    //nispero.createWorker().start()
    println(nispero.configuration)
  }

  override def launch(): Unit = {
    println("launching metamanager")
  }

}
