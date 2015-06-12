package ohnosequences.compota.aws

import java.io.File
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}
import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean}

import ohnosequences.awstools.AWSClients
import ohnosequences.compota.Namespace
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.environment.{InstanceId, AnyEnvironment}
import ohnosequences.logging.{S3Logger}
import scala.util.{Try}

class AwsEnvironment(val instanceId: InstanceId,
                     val namespace: Namespace,
                     val configuration: AwsCompotaConfiguration,
                     val awsClients: AWSClients,
                     val logger: S3Logger,
                     val workingDirectory: File,
                     val executor: ExecutorService,
                     val environments: ConcurrentHashMap[(InstanceId, Namespace), AwsEnvironment],
                     val errorTable: AwsErrorTable,
                     val sendForceUnDeployCommand0: (AwsEnvironment, String, String) => Try[Unit],
                     val rootEnvironment0: Option[AwsEnvironment],
                     val origin: Option[AwsEnvironment],
                     val localErrorCounts: AtomicInteger
) extends AnyEnvironment[AwsEnvironment] { awsEnvironment =>


  override def rootEnvironment: AwsEnvironment = rootEnvironment0 match {
    case None => awsEnvironment
    case Some(env) => env
  }


  override def terminate(): Unit = {
    //terminate instance
    Try {
      awsClients.ec2.terminateInstance(rootEnvironment.instanceId.id)
    }
  }

  override def subEnvironmentSync[R](subspaceOrInstance: Either[String, InstanceId])(statement: AwsEnvironment => R) : Try[(AwsEnvironment, R)] = {
    Try {
      subspaceOrInstance match {
        case Left(subspace) => {
          logger.debug("creating working directory: " + new File(workingDirectory, subspace).getAbsolutePath)
          val newWorkingDir = new File(workingDirectory, subspace)
          newWorkingDir.mkdir()
          val env = new AwsEnvironment(
            instanceId = instanceId,
            namespace = namespace / subspace,
            configuration = configuration,
            awsClients = awsClients,
            logger = logger.subLogger(subspace, reportOriginal = true, configuration.loggingDestination(instanceId, namespace / subspace)),
            workingDirectory = newWorkingDir,
            executor = executor,
            errorTable = errorTable,
            sendForceUnDeployCommand0 = sendForceUnDeployCommand0,
            environments = environments,
            rootEnvironment0 = Some(rootEnvironment),
            origin = Some(awsEnvironment),
            localErrorCounts = localErrorCounts
          )
          (env, statement(env))
        }
        case Right(instance) => {
          logger.debug("creating working directory: " + new File(workingDirectory, instance.id).getAbsolutePath)
          val newWorkingDir = new File(workingDirectory, instance.id)
          newWorkingDir.mkdir()
          val env = new AwsEnvironment(
            instanceId = instance,
            namespace = Namespace.root,
            configuration = configuration,
            awsClients = awsClients,
            logger = logger.subLogger(instance.id, reportOriginal = true, configuration.loggingDestination(instanceId, namespace / instance.id)),
            workingDirectory = newWorkingDir,
            executor = executor,
            errorTable = errorTable,
            sendForceUnDeployCommand0 = sendForceUnDeployCommand0,
            environments = environments,
            rootEnvironment0 = Some(rootEnvironment),
            origin = Some(awsEnvironment),
            localErrorCounts = localErrorCounts
          )
          (env, statement(env))
        }
      }


    }
  }

  def createDynamoDBContext: DynamoDBContext = {
    DynamoDBContext(awsClients, configuration.metadata, logger)
  }

  val isStoppedFlag = new AtomicBoolean(false)

  override def sendForceUnDeployCommand(reason: String, message: String): Try[Unit] = sendForceUnDeployCommand0(awsEnvironment, reason, message)

  override def isStopped: Boolean = isStoppedFlag.get()


  override def stop(): Unit ={
    isStoppedFlag.set(true)
  }
}


