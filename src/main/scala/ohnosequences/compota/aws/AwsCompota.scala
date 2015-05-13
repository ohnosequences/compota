package ohnosequences.compota.aws

import java.io.File

import com.amazonaws.auth.{InstanceProfileCredentialsProvider, AWSCredentialsProvider}
import ohnosequences.awstools.AWSClients
import ohnosequences.compota.aws.metamanager.AwsMetaManager
import ohnosequences.compota.aws.queues.{DynamoDBContext, DynamoDBQueue}
import ohnosequences.compota.metamanager.{BaseCommandSerializer, BaseMetaManagerCommand}
import ohnosequences.compota.queues._
import ohnosequences.compota.{TerminationDaemon, Compota}
import ohnosequences.logging.{S3Logger}

import scala.util.{Success, Failure, Try}


abstract class AwsCompota[U] (
   nisperos: List[AnyAwsNispero],
   reducers: List[AnyQueueReducer.of[AwsEnvironment]],
   val configuration: AwsCompotaConfiguration
                               )
  extends Compota[AwsEnvironment, AnyAwsNispero, U](nisperos, reducers, configuration) {

  def awsCredentialsProvider: AWSCredentialsProvider = {
    new InstanceProfileCredentialsProvider()
  }

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


  def logger(prefix: String, workingDir: File, logFile: File) = {
    new S3Logger(awsClients.s3, prefix, configuration.loggerDebug, workingDir, logFile)
  }

  def executeLocal(prefix: String)(statement: AwsEnvironment => Unit): Unit = {

  }

  def execute(prefix: String, workingDirectory: File, logFile: File, isMetaManager: Boolean)(statement: AwsEnvironment => Unit): Unit = {

    val instanceID = awsClients.ec2.getCurrentInstanceId.getOrElse("unknown")

    workingDirectory.mkdir()
    var env: Option[AwsEnvironment] = None
    val envLogger = logger(prefix, workingDirectory, logFile)
    ErrorTable.apply(envLogger, configuration.errorTable, awsClients).recoverWith { case t =>
      envLogger.error(t)
      Failure(t)
    }.foreach { errorTable =>
      object thread extends Thread(prefix) {
        env = Some(new AwsEnvironment(
          awsClients = awsClients,
          awsCompotaConfiguration = configuration,
          logger0 = envLogger,
          workingDirectory = workingDirectory,
          awsInstanceId = instanceID,
          errorTable = errorTable,
          sendUnDeployCommand,
          isMetaManager
        ))
        override def run(): Unit ={
          env match {
            case Some(e) =>  statement(e)
            case None => envLogger.error("initialization error")
          }
        }
      }
      thread.start()
      env match {
        case Some(e) =>  statement(e)
        case None => {
          envLogger.error("initialization error")
          awsClients.ec2.getCurrentInstance.foreach(_.terminate())
        }
      }
    }
  }


  override def launchMetaManager(): Unit = {
    val metaManager = new AwsMetaManager[U](AwsCompota.this)
    execute("metamanager",
      new File(configuration.workingDirectory),
      new File(configuration.workingDirectory, "metamanager.log"),
      isMetaManager = true) {
      env => metaManager.launchMetaManager(env, controlQueue, { e: AwsEnvironment => DynamoDBContext(
        env.awsClients,
        env.awsCompotaConfiguration.metadata,
        env.logger)})
    }
  }



  override def launchTerminationDaemon(terminationDaemon: TerminationDaemon[CompotaEnvironment]): Try[Unit] = {
    execute("terminationDaemon",
      new File(configuration.workingDirectory),
      new File(configuration.workingDirectory, "terminationDaemon.log"),
      isMetaManager = false
    ) { env =>
      terminationDaemon.start(env)
    }
    Success(())
  }

  override def launchWorker(nispero: AnyAwsNispero): Unit = {
    val prefix = "worker_" + nispero.name
    execute(
      prefix,
      new File(configuration.workingDirectory, prefix),
      new File(configuration.workingDirectory, prefix + ".log"),
      false
    ) {
      env => nispero.createWorker().start(env)
    }
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



  override def launch(): Try[Unit] = ???

  override def finishUnDeploy(env: AwsEnvironment,  reason: String, message: String): Try[Unit] = ???

  override def sendUnDeployCommand(env: AwsEnvironment, reason: String, force: Boolean): Try[Unit] = ???

  override def addTasks(environment: CompotaEnvironment): Try[Unit] = ???

  override def startedTime(): Try[Long] = ???

  override def tasksAdded(): Try[Boolean] = ???

  override def setTasksAdded(): Try[Unit] = ???
}
