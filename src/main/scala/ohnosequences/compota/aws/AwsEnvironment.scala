package ohnosequences.compota.aws

import java.io.File
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}
import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean}

import ohnosequences.awstools.AWSClients
import ohnosequences.compota.local.LocalErrorTable
import ohnosequences.compota.{ErrorTable, Namespace}
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.environment.{InstanceId, AnyEnvironment}
import ohnosequences.logging.{S3Logger, Logger}
import scala.util.{Try}
import scala.collection.JavaConversions._

class AwsEnvironment(val instanceId: InstanceId,
                     val namespace: Namespace,
                     val configuration: AwsCompotaConfiguration,
                     val awsClients: AWSClients,
                     val logger: Logger,
                     val workingDirectory: File,
                     val executor: ExecutorService,
                     val environments: ConcurrentHashMap[(InstanceId, Namespace), AwsEnvironment],
                     val errorTable: ErrorTable,
                     val forceUndeploy0: (AwsEnvironment, String, String) => Try[Unit],
                     val originEnvironment: Option[AwsEnvironment],
                     val localErrorCounts: AtomicInteger) extends AnyEnvironment[AwsEnvironment] {
  awsEnvironment =>


  override def terminate(): Unit = {
    //terminate instance
    Try {
      //awsClients.ec2.terminateInstance(instanceId.id)
      logger.warn("termination disabled")
    }
  }


  override def subEnvironment(subspace: String): Try[AwsEnvironment] = {
    Try {
      val newWorkingDirectory = new File(workingDirectory, subspace)
      logger.debug("creating working directory: " + newWorkingDirectory.getAbsolutePath)
      newWorkingDirectory.mkdir()
      val newNamespace = namespace / subspace
      val newLogger = logger match {
        case s3Logger: S3Logger => {
          s3Logger.subLogger(subspace, configuration.loggingDestination(instanceId, newNamespace))
        }
        case _ => {
          logger.subLogger(subspace)
        }
      }
      new AwsEnvironment(
        instanceId = instanceId,
        namespace = newNamespace,
        configuration = configuration,
        awsClients = awsClients,
        logger = newLogger,
        workingDirectory = newWorkingDirectory,
        executor = executor,
        errorTable = errorTable,
        forceUndeploy0 = forceUndeploy0,
        environments = environments,
        originEnvironment = Some(awsEnvironment),
        localErrorCounts = localErrorCounts
      )
    }
  }


  def createDynamoDBContext: DynamoDBContext = {
    DynamoDBContext(awsClients, configuration.metadata, logger)
  }

  val isStoppedFlag = new AtomicBoolean(false)

  override def forceUndeploy(reason: String, message: String): Try[Unit] = forceUndeploy0(awsEnvironment, reason, message)

  override def isStopped: Boolean = isStoppedFlag.get()

  override def stop(): Unit = {
    isStoppedFlag.set(true)
  }

  override val localErrorTable: ErrorTable = new LocalErrorTable
}


