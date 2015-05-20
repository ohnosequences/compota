package ohnosequences.compota.aws

import java.io.File
import java.util.concurrent.Executors

import com.amazonaws.auth.{InstanceProfileCredentialsProvider, AWSCredentialsProvider}
import ohnosequences.awstools.AWSClients
import ohnosequences.compota.aws.metamanager.AwsMetaManager
import ohnosequences.compota.aws.queues.{DynamoDBContext, DynamoDBQueue}
import ohnosequences.compota.console.AnyConsole
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.metamanager.{BaseCommandSerializer, BaseMetaManagerCommand}
import ohnosequences.compota.queues._
import ohnosequences.compota.{TerminationDaemon, Compota}

import scala.util.{Success, Failure, Try}


abstract class AwsCompota[U] (
   nisperos: List[AnyAwsNispero],
   reducers: List[AnyQueueReducer.of[AwsEnvironment]],
   val awsConfiguration: AwsCompotaConfiguration
                               )
  extends Compota[AwsEnvironment, AnyAwsNispero, U](nisperos, reducers, awsConfiguration) {

  def awsCredentialsProvider: AWSCredentialsProvider = {
    new InstanceProfileCredentialsProvider()
  }

  val executor = Executors.newCachedThreadPool()

  val controlQueue = new DynamoDBQueue[BaseMetaManagerCommand]("controlQueue", BaseCommandSerializer)

  var awsClients0: Option[AWSClients] = None
  def awsClients: AWSClients = awsClients0 match {
    case None => {
      val aws = AWSClients.create(awsCredentialsProvider)
      awsClients0 = Some(aws)
      aws
    }
    case Some(aws) => aws
  }



  override def launchConsole(nisperoGraph: NisperoGraph, env: CompotaEnvironment): Try[AnyConsole] = ???


  override def launchMetaManager(): Try[CompotaEnvironment] = {
    val metaManager = new AwsMetaManager[U](AwsCompota.this)

    AwsEnvironment.execute(
      executor,
      "metamanager",
      awsClients,
      awsConfiguration.workingDirectory,
      awsConfiguration.loggingDirectory,
      awsConfiguration,
      sendUnDeployCommand,
      isMetaManager = true) { env =>

      metaManager.launchMetaManager(env, controlQueue, { e: AwsEnvironment =>
        DynamoDBContext(
          env.awsClients,
          env.awsCompotaConfiguration.metadata,
          env.logger)},
        launchTerminationDaemon,
        launchConsole
      )
    }
  }



  override def launchWorker(nispero: AnyAwsNispero): Try[CompotaEnvironment] = {
    val prefix = "worker_" + nispero.configuration.name
    AwsEnvironment.execute(
      executor,
      prefix,
      awsClients,
      awsConfiguration.workingDirectory,
      awsConfiguration.loggingDirectory,
      awsConfiguration,
      sendUnDeployCommand,
      isMetaManager = false) {
      env => nispero.createWorker().start(env)
    }
  }

  override def createNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit] = {
    Try {
      env.logger.info("creating working auto scaling group " + nispero.awsConfiguration.workerAutoScalingGroup.name)
      env.awsClients.as.createAutoScalingGroup(nispero.awsConfiguration.workerAutoScalingGroup)
      ()
    }
  }

  override def deleteNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit] = {
    Try {
      val group = nispero.awsConfiguration.workerAutoScalingGroup.name
      env.logger.info("deleting auto scaling group: " + group)
      env.awsClients.as.deleteAutoScalingGroup(group)
      ()
    }
  }

  override def deleteManager(env: CompotaEnvironment): Try[Unit] = {
    Try {
      val group = awsConfiguration.managerAutoScalingGroup
      env.logger.info("deleting auto scaling group: " + group.name)
      env.awsClients.as.deleteAutoScalingGroup(group)
      ()
    }
  }



  override def launch(): Try[Unit] = ???

  override def finishUnDeploy(env: AwsEnvironment,  reason: String, message: String): Try[Unit] = ???

  override def sendUnDeployCommand(env: AwsEnvironment, reason: String, force: Boolean): Try[Unit] = ???

  override def addTasks(environment: CompotaEnvironment): Try[Unit] = ???

  override def startedTime(): Try[Long] = ???

  override def tasksAdded(): Try[Boolean] = ???

  override def setTasksAdded(): Try[Unit] = ???

}
