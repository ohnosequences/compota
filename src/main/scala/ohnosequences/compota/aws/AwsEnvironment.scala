package ohnosequences.compota.aws

import java.io.File
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}
import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean}

import ohnosequences.awstools.AWSClients
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
                     val sendForceUnDeployCommand0: (AwsEnvironment, String, String) => Try[Unit],
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

  override def subEnvironmentSync[R](subspaceOrInstance: Either[String, InstanceId], async: Boolean)(statement: AwsEnvironment => R): Try[(AwsEnvironment, R)] = {
    Try {
      val (newInstance, newNamespace, newLogger, newWorkingDirectory) = subspaceOrInstance match {
        case Left(subspace) => {
          val nWorkingDirectory = new File(workingDirectory, subspace)
          logger.debug("creating working directory: " + nWorkingDirectory.getAbsolutePath)
          nWorkingDirectory.mkdir()
          val nNamespace =  namespace / subspace
          val nLogger = logger match {
            case s3Logger: S3Logger => {
              s3Logger.subLogger(subspace, configuration.loggingDestination(instanceId, nNamespace))
            }
            case _ => {
              logger.subLogger(subspace)
            }
          }
          (instanceId, namespace / subspace, nLogger, nWorkingDirectory)
        }
        case Right(instance) => {
          logger.warn("custom instances are not supported by AwsEnvironment")
          (instanceId, namespace, logger, workingDirectory)
        }
      }
      new AwsEnvironment(
        instanceId = newInstance,
        namespace = newNamespace,
        configuration = configuration,
        awsClients = awsClients,
        logger = newLogger,
        workingDirectory = newWorkingDirectory,
        executor = executor,
        errorTable = errorTable,
        sendForceUnDeployCommand0 = sendForceUnDeployCommand0,
        environments = environments,
        originEnvironment = Some(awsEnvironment),
        localErrorCounts = localErrorCounts
      )
    }.flatMap { env =>
      environments.put((env.instanceId, env.namespace), env)
      val res = Try {
        logger.info(environments.toString)
        (env, statement(env))
      }
      if (!async) {
        environments.remove((env.instanceId, env.namespace))
      }
      res
    }
  }

  def createDynamoDBContext: DynamoDBContext = {
    DynamoDBContext(awsClients, configuration.metadata, logger)
  }

  val isStoppedFlag = new AtomicBoolean(false)

  override def sendForceUnDeployCommand(reason: String, message: String): Try[Unit] = sendForceUnDeployCommand0(awsEnvironment, reason, message)

  override def isStopped: Boolean = isStoppedFlag.get()

  override def stop(recursive: Boolean): Unit = {
    isStoppedFlag.set(true)
    if (recursive) {
      environments.foreach { case ((i, n), e) => e.stop(false)}
    }
  }
}


