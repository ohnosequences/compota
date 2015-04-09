package ohnosequences.compota.aws

import java.io.File

import ohnosequences.compota.queues._
import ohnosequences.compota.{Compota}
//import ohnosequences.nisperon.AWS

import scala.util.Try

abstract class AwsCompota(
   nisperos: List[AwsNisperoAux],
   reducers: List[AnyQueueReducer.of[AwsEnvironment]],
   configuration: AwsCompotaConfigurationAux) extends Compota[AwsEnvironment, AwsNisperoAux](nisperos, reducers) {


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

  override def addTasks(): Unit = ???

}
