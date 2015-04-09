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


  override def launchWorker(nispero: AwsNisperoAux): Unit = {

    //nispero.createWorker().start()
    println(nispero.configuration)
  }

  override def addTasks(): Unit = ???

}
