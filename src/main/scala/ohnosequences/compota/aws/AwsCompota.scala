package ohnosequences.compota.aws

import java.util.concurrent.{ConcurrentHashMap, Executors}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import com.amazonaws.auth.{InstanceProfileCredentialsProvider, AWSCredentialsProvider}
import ohnosequences.awstools.AWSClients
import ohnosequences.compota.aws.metamanager.AwsMetaManager
import ohnosequences.compota.console.{UnfilteredConsoleServer, AnyConsole}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.{QueueChecker}
import ohnosequences.compota.metamanager.{ForceUnDeploy, UnDeploy}
import ohnosequences.compota.queues._
import ohnosequences.compota.{Namespace, AnyCompota}
import ohnosequences.logging.{S3Logger}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object AnyAwsCompota {
  type of[U] = AnyAwsCompota { type CompotaUnDeployActionContext = U}
  type ofN[N <: AnyAwsNispero] = AnyAwsCompota { type CompotaNispero = N }
}

trait AnyAwsCompota extends AnyCompota { awsCompota =>
  override type CompotaEnvironment = AwsEnvironment
  override type CompotaNispero = AnyAwsNispero
  override type CompotaMetaManager = AwsMetaManager[CompotaUnDeployActionContext]

  type CompotaConfiguration <: AwsCompotaConfiguration


  lazy val awsCredentialsProvider: AWSCredentialsProvider = {
    new InstanceProfileCredentialsProvider()
  }

  override val metaManager: CompotaMetaManager = new AwsMetaManager[CompotaUnDeployActionContext](AnyAwsCompota.this)

  val executor = Executors.newCachedThreadPool()

  val awsClients0: AtomicReference[Option[AWSClients]] = new AtomicReference(None)
  def awsClients: AWSClients = {
    awsClients0.get match {
      case None => {
        val aws = AWSClients.create(awsCredentialsProvider)
        awsClients0.set(Some(aws))
        aws
      }
      case Some(aws) => aws
    }
  }

  def launchLogUploader(): Unit = {
    initialEnvironment.map { env =>
      env.logger.info("starting log uploader")
      configuration.loggingLocation(env.instanceId) match {
        case None => env.logger.info("log uploader is disabled")
        case Some(loggerDestination) => {
          env.subEnvironmentAsync("logUploader") { logEnv =>
            @tailrec
            def launchLogUploaderRec(timeout: Duration = configuration.logUploaderTimeout): Unit = {
              if(env.isStopped) {
                env.logger.info("log uploader stopped")
                Success(())
              } else {
                Thread.sleep(timeout.toMillis)
                env.logger.info("uploading log " + logEnv.logger.logFile.getAbsolutePath + " to " + loggerDestination)
                logEnv.logger.uploadLog() match {
                  case Failure(t) => logEnv.reportError(Namespace.logUploader, t)
                  case Success(uploaded) => launchLogUploaderRec(timeout)
                }
              }
            }
            launchLogUploaderRec()
          }
        }
      }
    }

  }

  override def launchConsole(nisperoGraph: QueueChecker[CompotaEnvironment], controlQueue: AnyQueueOp, env: CompotaEnvironment): Try[AnyConsole] = {
    Try {
      val console = new AwsConsole[CompotaNispero](awsCompota, env, controlQueue, nisperoGraph)
      new UnfilteredConsoleServer(console).start()
      console
    }
  }

  private val initialEnvironment_ = new AtomicReference[Option[AwsEnvironment]](None)
  override def initialEnvironment: Try[AwsEnvironment] = {
    initialEnvironment_.get match {
      case Some(env) => Success(env)
      case None => {
        val ec2InstanceId = awsClients.ec2.getCurrentInstanceId.getOrElse("unknown_" + System.currentTimeMillis())
        configuration.workingDirectory.mkdir()
        S3Logger(
          awsClients.s3,
          ec2InstanceId,
          configuration.loggingDirectory,
          "log.txt",
          configuration.loggingLocation(ec2InstanceId),
          debug = configuration.loggingDebug,
          printToConsole = configuration.loggersPrintToConsole
        ).flatMap { logger =>
          AwsErrorTable.apply(logger, configuration.errorTable, awsClients).map { errorTable =>
            new AwsEnvironment(
              instanceId = InstanceId(ec2InstanceId),
              configuration = configuration,
              awsClients = awsClients,
              logger = logger,
              workingDirectory = configuration.workingDirectory,
              executor = executor,
              errorTable = errorTable,
              sendForceUnDeployCommand0 = sendForceUnDeployCommand,
              environments = new ConcurrentHashMap[InstanceId, AwsEnvironment],
              rootEnvironment0 = None,
              origin = None,
              localErrorCounts = new AtomicInteger(0)
            )
          }
        }.map { env =>
          initialEnvironment_.set(Some(env))
          env
        }
      }
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

  override def launch(): Try[CompotaEnvironment] = ???

  override def finishUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit] = ???

  override def prepareUnDeployActions(env: CompotaEnvironment): Try[CompotaUnDeployActionContext] = ???

  //undeploy right now
  override def forceUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit] = ???

  override def sendUnDeployCommand(env: CompotaEnvironment): Try[Unit] = {
    metaManager.sendMessageToControlQueue(env, UnDeploy)
  }

  override def sendForceUnDeployCommand(env: CompotaEnvironment, reason: String, message: String): Try[Unit] = {
    metaManager.sendMessageToControlQueue(env, ForceUnDeploy(reason, message))
  }

  override def startedTime(env: CompotaEnvironment): Try[Long] = {
    env.awsClients.as.getCreatedTimeTry(configuration.managerAutoScalingGroup.name).map { date =>
      date.getTime
    }
  }

  override def compotaDeployed(env: CompotaEnvironment): Try[Boolean] = {
    Success(()).flatMap { u =>
      nisperos.headOption match {
        case None => Failure(new Error("nispero list is empty"))
        case Some(nispero) =>  Success(env.awsClients.as.getAutoScalingGroupByName(nispero.configuration.workerAutoScalingGroup.name).isDefined)
      }
    }
  }
}


abstract class AwsCompota[U] (
   val nisperos: List[AnyAwsNispero],
   val reducers: List[AnyQueueReducer.of[AwsEnvironment]],
   val configuration: AwsCompotaConfiguration)
  extends AnyAwsCompota {

  override type CompotaUnDeployActionContext = U

//  override type CompotaNispero = AnyAwsNispero

  override type CompotaConfiguration = AwsCompotaConfiguration

}
