package ohnosequences.compota.aws

import java.io.File

import ohnosequences.compota.queues._
import ohnosequences.compota.{AnyCompota, Compota}
//import ohnosequences.nisperon.AWS

import scala.util.Try


abstract class AwsCompota[U] (
   nisperos: List[AnyAwsNispero],
   reducers: List[AnyQueueReducer.of[AwsEnvironment]],
   val configuration: AwsCompotaConfigurationAux) extends Compota[AwsEnvironment, AnyAwsNispero, U](nisperos, reducers, configuration) {




 // val aws = new AWS(new File("."))



  override def launchWorker(nispero: AnyAwsNispero): Unit = {

    //nispero.createWorker().start()
    println(nispero.configuration)
  }

  override def createNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit] = {
    Try {
      env.logger.info("creating working auto scaling group " + nispero.configuration.workerAutoScalingGroup.name)
      env.awsClients.as.createAutoScalingGroup(nispero.configuration.workerAutoScalingGroup)
      ()
    }
  }

  override def deleteNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit] = {
    Try {
      val group = nispero.configuration.workerAutoScalingGroup.name
      env.logger.info("deleting auto scaling group: " + group)
      env.awsClients.as.deleteAutoScalingGroup(group)
      ()
    }
  }

  override def deleteManager(env: CompotaEnvironment): Try[Unit] = {
    Try {
      val group = configuration.managerAutoScalingGroup
      env.logger.info("deleting auto scaling group: " + group.name)
      env.awsClients.as.deleteAutoScalingGroup(group)
      ()
    }
  }
}
