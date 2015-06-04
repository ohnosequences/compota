package ohnosequences.compota.aws

import java.util.concurrent.Executors

import com.amazonaws.auth.{InstanceProfileCredentialsProvider, AWSCredentialsProvider}
import ohnosequences.awstools.AWSClients
import ohnosequences.compota.aws.metamanager.AwsMetaManager
import ohnosequences.compota.aws.queues.{DynamoDBContext, DynamoDBQueue}
import ohnosequences.compota.console.AnyConsole
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.metamanager.{BaseCommandSerializer, BaseMetaManagerCommand}
import ohnosequences.compota.queues._
import ohnosequences.compota.{AnyCompota}

import scala.util.{Try}

object AnyAwsCompota {
  type of[U] = AnyAwsCompota { type CompotaUnDeployActionContext = U}
}

trait AnyAwsCompota extends AnyCompota {
  type CompotaEnvironment = AwsEnvironment
  type CompotaNispero <: AnyAwsNispero
  type MetaManager = AwsMetaManager[CompotaUnDeployActionContext]

  type CompotaConfiguration <: AwsCompotaConfiguration


  def awsCredentialsProvider: AWSCredentialsProvider = {
    new InstanceProfileCredentialsProvider()
  }



 // val awsConfiguration: AwsCompotaConfiguration

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

  def launchConsole(nisperoGraph: NisperoGraph, env: CompotaEnvironment): Try[AnyConsole] = ???

  override def launchMetaManager(): Try[CompotaEnvironment] = {
    val metaManager = new AwsMetaManager[CompotaUnDeployActionContext](AnyAwsCompota.this)

    AwsEnvironment.execute(
      executor,
      "metamanager",
      awsClients,
      configuration.workingDirectory,
      configuration.loggingDirectory,
      configuration,
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


  override def launchWorker(nispero: CompotaNispero): Try[CompotaEnvironment] = {
    val prefix = "worker_" + nispero.configuration.name
    AwsEnvironment.execute(
      executor,
      prefix,
      awsClients,
      configuration.workingDirectory,
      configuration.loggingDirectory,
      configuration,
      sendUnDeployCommand,
      isMetaManager = false) {
      env => nispero.createWorker().start(env)
    }
  }


  override def createNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit] = {
    Try {
      env.logger.info("creating working auto scaling group " + nispero.configuration.workerAutoScalingGroup.name)
      env.awsClients.as.createAutoScalingGroup(nispero.configuration.workerAutoScalingGroup)
      ()
    }
  }

  override def deleteNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit] = {
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

  override def launch(): Try[Unit] = ???

  override def unDeployActions(force: Boolean, env: CompotaEnvironment, context: CompotaUnDeployActionContext): Try[String] = ???

  override def finishUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit] = ???

  override def prepareUnDeployActions(env: CompotaEnvironment): Try[CompotaUnDeployActionContext] = ???

  override def sendUnDeployCommand(env: CompotaEnvironment, reason: String, force: Boolean): Try[Unit] = ???

  override def startedTime(): Try[Long] = ???

  override def tasksAdded(): Try[Boolean] = ???

  override def setTasksAdded(): Try[Unit] = ???
}


abstract class AwsCompota[U] (
   val nisperos: List[AnyAwsNispero],
   val reducers: List[AnyQueueReducer.of[AwsEnvironment]],
   val configuration: AwsCompotaConfiguration)
  extends AnyAwsCompota {

  override type CompotaUnDeployActionContext = U

  override type CompotaNispero = AnyAwsNispero

  override type CompotaConfiguration = AwsCompotaConfiguration

}
