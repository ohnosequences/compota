package ohnosequences.compota.aws

import java.io.File
import java.util.concurrent.{ConcurrentHashMap, Executors}
import java.util.concurrent.atomic.{AtomicInteger}

import com.amazonaws.auth.{PropertiesFileCredentialsProvider}
import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.compota.aws.metamanager.AwsMetaManager
import ohnosequences.compota.console.{UnfilteredConsoleServer, AnyConsole}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.{QueueChecker}
import ohnosequences.compota.local.LocalErrorTable
import ohnosequences.compota.metamanager.{ForceUnDeploy, UnDeploy}
import ohnosequences.compota.queues._
import ohnosequences.compota.{Namespace, AnyCompota}
import ohnosequences.logging.{ConsoleLogger, S3Logger}

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

  override val metaManager: CompotaMetaManager = new AwsMetaManager[CompotaUnDeployActionContext](AnyAwsCompota.this)

  def launchLogUploader(env: CompotaEnvironment): Unit = {
    env.logger.info("starting log uploader")
    env.logger match {
      case s3Logger: S3Logger => {
        s3Logger.loggingDestination match {
          case None => env.logger.info("log uploader is disabled")
          case Some(loggerDestination) => {
            env.subEnvironmentAsync(Left(Namespace.logUploader)) { logEnv =>
              @tailrec
              def launchLogUploaderRec(timeout: Duration = configuration.logUploaderTimeout): Unit = {
                if (env.isStopped) {
                  env.logger.info("log uploader stopped")
                  Success(())
                } else {
                  Thread.sleep(timeout.toMillis)
                  env.logger.info("uploading log " + s3Logger.logFile.getAbsolutePath + " to " + loggerDestination)
                  s3Logger.uploadLog() match {
                    case Failure(t) => logEnv.reportError(t)
                    case Success(uploaded) => launchLogUploaderRec(timeout)
                  }
                }
              }
              launchLogUploaderRec()
            }
          }
        }
      }
      case _ => {
        env.logger.info("starting log uploader: unsupported logger")
      }
    }

  }

  override def launchConsole(nisperoGraph: QueueChecker[CompotaEnvironment], controlQueue: AnyQueueOp, env: CompotaEnvironment): Try[AnyConsole] = {
    Try {
      //todo move to separated command

      env.awsClients.s3.createBucket(configuration.resultsBucket)
      env.awsClients.s3.createBucket(configuration.loggerBucket)

      val console = new AwsConsole[CompotaNispero](awsCompota, env, controlQueue, nisperoGraph)
      val currentAddress = env.awsClients.ec2.getCurrentInstance.flatMap {_.getPublicDNS()}.getOrElse("<undefined>")
      val server = new UnfilteredConsoleServer(console, currentAddress)
      server.start()

      val message = server.startedMessage(customMessage)
      env.logger.info("sending notification" + configuration.name + " started")
      sendNotification(env, configuration.name + " started", message)
      console
    }
  }

  override def initialEnvironment: Try[AwsEnvironment] = {

    val awsClients = AWSClients.create(configuration.localAwsCredentialsProvider, configuration.awsRegion)

    val ec2InstanceId = awsClients.ec2.getCurrentInstanceId.getOrElse("unknown_" + System.currentTimeMillis())
    configuration.workingDirectory.mkdir()
    S3Logger(
      awsClients.s3,
      ec2InstanceId,
      configuration.loggingDirectory,
      "log.txt",
      configuration.loggingDestination(InstanceId(ec2InstanceId), Namespace.root),
      debug = configuration.loggerDebug,
      printToConsole = configuration.loggersPrintToConsole
    ).flatMap { logger =>
      AwsErrorTable.apply(logger, configuration.errorTable, awsClients).map { errorTable =>
        new AwsEnvironment(
          instanceId = InstanceId(ec2InstanceId),
          namespace = Namespace.root,
          configuration = configuration,
          awsClients = awsClients,
          logger = logger,
          workingDirectory = configuration.workingDirectory,
          executor = Executors.newCachedThreadPool(),
          errorTable = errorTable,
          sendForceUnDeployCommand0 = sendForceUnDeployCommand,
          environments = new ConcurrentHashMap[(InstanceId, Namespace), AwsEnvironment](),
          originEnvironment = None,
          localErrorCounts = new AtomicInteger(0)
        )
      }
    }
  }

  //to start compota
  override def localEnvironment(cliLogger: ConsoleLogger, args: List[String]): Try[AwsEnvironment] = {
    Try {
      val awsClients = args match {
        case file :: Nil => {
          AWSClients.create(new PropertiesFileCredentialsProvider(file), configuration.awsRegion)
        }
        case _ => {
          AWSClients.create(configuration.localAwsCredentialsProvider, configuration.awsRegion)
        }
      }
      val localInstanceId = configuration.initialEnvironmentId
      new AwsEnvironment(
        instanceId = localInstanceId,
        namespace = Namespace.root,
        configuration = configuration,
        awsClients = awsClients,
        logger = cliLogger,
        workingDirectory = new File("."),
        executor = Executors.newCachedThreadPool(),
        errorTable = new LocalErrorTable,
        sendForceUnDeployCommand0 = sendForceUnDeployCommand,
        environments = new ConcurrentHashMap[(InstanceId, Namespace), AwsEnvironment](),
        originEnvironment = None,
        localErrorCounts = new AtomicInteger(0)
      )
    }
  }


  override def configurationChecks(env: AwsEnvironment): Try[Boolean] = {
    super.configurationChecks(env).flatMap { u =>
      env.logger.info("checking jar object " + configuration.metadata.jarUrl)
      ObjectAddress(configuration.metadata.jarUrl).flatMap { jarObject =>
        env.awsClients.s3.objectExists(jarObject).flatMap {
          case true => {
            env.logger.info("checking notification e-mail: " + configuration.notificationEmail)
            if(configuration.notificationEmail.isEmpty) {
              Failure(new Error("notification email is empty"))
            } else {
              env.logger.info("checking notification topic")
              Try {env.awsClients.sns.createTopic(configuration.notificationTopic)}.flatMap { topic =>
                if (!topic.isEmailSubscribed(configuration.notificationEmail)) {
                  topic.subscribeEmail(configuration.notificationEmail)
                  env.logger.info("please confirm subscription")
                  Failure(new Error("email " + configuration.notificationEmail + " is not subscribed to the notification topic"))
                } else {
                  Success(true)
                }
              }.flatMap { t =>
                env.logger.info("checking ssh key pair")
                if(configuration.keyName.isEmpty) {
                  Failure(new Error("ssh key pais is not specified"))
                } else {
                  Success(true)
                }
              }
            }
          }
          case false => Failure(new Error("jar object " + jarObject.url + " does not exists"))
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

  def createMetaManagerGroup(env: CompotaEnvironment): Try[Unit] = {
    Try {
      env.logger.info("creating metamanager auto scaling group " + configuration.managerAutoScalingGroup.name)
      env.awsClients.as.createAutoScalingGroup(configuration.managerAutoScalingGroup)
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

  override def launch(env: CompotaEnvironment): Try[CompotaEnvironment] = {
    createMetaManagerGroup(env).map { r =>
      env
    }
  }

  override def finishUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit] = {
    Success(())
  }


  override def sendNotification(env: AwsEnvironment, subject: String, message: String): Try[Unit] = {
    Try {
      env.logger.debug("sendting notification to the topic " + configuration.notificationTopic)
      val topic = env.awsClients.sns.createTopic(configuration.notificationTopic)

      topic.publish(message, subject)
    }
  }

  //undeploy right now
  override def forceUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit] = {
    val logger = env.logger
    nisperos.foreach { nispero =>
      Try {
        deleteNisperoWorkers(env, nispero)
      }
      Try {
        logger.warn("deleting queue: " + nispero.inputQueue.name)
        nispero.inputQueue.delete(nispero.inputContext(env))
      }
      Try {
        logger.warn("deleting queue: " + nispero.outputQueue.name)
        nispero.outputQueue.delete(nispero.outputContext(env))
      }
    }


    Success(()).flatMap { u =>
      forcedUnDeployActions(env)
    } match {
      case Success(m) => {
        sendNotification(env, configuration.name + " terminated", "reason: " + reason + System.lineSeparator() + message + System.lineSeparator() + m)
      }
      case Failure(t) => sendNotification(env, configuration.name + " terminated", "reason: " + reason + System.lineSeparator() + message)
    }
    Try {
      logger.warn("deleting control queue: " + metaManager.controlQueue.name)
      metaManager.controlQueue.delete(metaManager.controlQueueContext(env))
    }
    Try {
      deleteManager(env)
    }
    env.stop(true)
    env.terminate()
    Success(())
  }

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
   val configuration: AwsCompotaConfiguration)
  extends AnyAwsCompota {

  override type CompotaUnDeployActionContext = U

  override type CompotaConfiguration = AwsCompotaConfiguration

}
