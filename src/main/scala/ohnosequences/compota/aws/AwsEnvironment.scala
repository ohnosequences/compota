package ohnosequences.compota.aws

import java.io.File
import java.util.concurrent.{Executors, ExecutorService}
import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean}

import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.model.{ScalarAttributeType, AttributeDefinition}
import ohnosequences.awstools.AWSClients
import ohnosequences.compota.Namespace
import ohnosequences.compota.aws.deployment.Metadata
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.environment.{InstanceId, AnyEnvironment}
import ohnosequences.logging.{S3Logger, Logger}

import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure, Try}


object AwsEnvironment {
  def execute(executor: ExecutorService,
              prefix: String,
              awsClients: AWSClients,
              workingDirectory: File,
              loggingDirectory: File,
              awsConfiguration: AwsCompotaConfiguration,
              sendUnDeployCommand: (AwsEnvironment, String, Boolean) => Try[Unit],
              isMetaManager: Boolean
               )(statement: AwsEnvironment => Unit): Try[AwsEnvironment] = {

    Success(()).flatMap { u =>
      val instanceID = awsClients.ec2.getCurrentInstanceId match {
        case None => prefix + "_unknown_" + System.currentTimeMillis()
        case Some(id) => prefix + "_id"
      }
      workingDirectory.mkdir()
      S3Logger(awsClients.s3, prefix, loggingDirectory, "log.txt", awsConfiguration.loggerBucket, awsConfiguration.loggingDebug).flatMap { envLogger =>
        AwsErrorTable.apply(envLogger, awsConfiguration.errorTable, awsClients).recoverWith { case t =>
          envLogger.error(t)
          Failure(t)
        }.flatMap { errorTable =>

          val env = new AwsEnvironment(
            awsClients = awsClients,
            awsCompotaConfiguration = awsConfiguration,
            logger = envLogger,
            workingDirectory = workingDirectory,
            awsInstanceId = instanceID,
            errorTable = errorTable,
            sendUnDeployCommand,
            isMetaManager
          )

          executor.execute(new Runnable {
            override def run(): Unit = {
              statement(env)
            }
          })
          Success(env)
        }
      }
    }
  }
}

class AwsEnvironment(val awsClients: AWSClients,
                     val awsCompotaConfiguration: AwsCompotaConfiguration,
                     val logger: S3Logger,
                     val workingDirectory: File,
                     val awsInstanceId: String,
                     val errorTable: AwsErrorTable,
                     val sendUnDeployCommand0: (AwsEnvironment, String, Boolean) => Try[Unit],
                     val isMetaManager: Boolean
                      ) extends AnyEnvironment[AwsEnvironment] { awsEnvironment =>


  override def subEnvironmentSync[R](suffix: String)(statement: AwsEnvironment => R) : Try[(AwsEnvironment, R)] = {
    Try {
      val env = new AwsEnvironment(
        awsClients,
        awsCompotaConfiguration,
        logger.subLogger(suffix, true),
        new File(workingDirectory, suffix),
        awsInstanceId,
        errorTable,
        sendUnDeployCommand0,
        isMetaManager
      )
      logger.debug("creating working directory: " + new File(workingDirectory, suffix).getAbsolutePath)
      new File(workingDirectory, suffix).mkdir()
      (env, statement(env))
    }
  }


  override def subEnvironment(suffix: String)(statement: (AwsEnvironment) => Unit): Try[AwsEnvironment] = {
    subEnvironmentSync(suffix) { env =>
      executor.execute(new Runnable {
        override def run(): Unit = {

          val oldName = Thread.currentThread().getName
          Thread.currentThread().setName(instanceId.id)
          env.logger.debug("changing thread to " + instanceId.id)

          statement(env)

          Thread.currentThread().setName(oldName)
        }
      })
    }.map(_._1)
  }

  override val executor: ExecutorService = Executors.newCachedThreadPool()

  def createDynamoDBContext(): DynamoDBContext = {
    DynamoDBContext(awsClients, awsCompotaConfiguration.metadata, logger)
  }

  val isTerminatedFlag = new AtomicBoolean(false)

  val localErrorCount = new AtomicInteger(0)

  override def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit] = sendUnDeployCommand0(awsEnvironment, reason, force)

  def toDynamoDBContext: DynamoDBContext = {
    DynamoDBContext(awsClients, awsCompotaConfiguration.metadata, logger)
  }

  override def instanceId: InstanceId = {
    InstanceId(awsInstanceId)
  }

  override def isStopped: Boolean = isTerminatedFlag.get()


  override def stop(): Unit ={
    isTerminatedFlag.set(true)
    awsClients.ec2.terminateInstance(awsInstanceId)
  }

  override def reportError(namespace: Namespace, t: Throwable): Unit = {

    logger.error(t)
    val stackTrace = new StringBuilder()
    logger.printThrowable(t, {s => stackTrace.append(s + System.lineSeparator())})

    errorTable.getNamespaceErrorCount(namespace).flatMap { globalCount =>
      if (globalCount > awsCompotaConfiguration.globalErrorThresholdPerNameSpace) {
        sendUnDeployCommand("reached error threshold for " + namespace.toString, force = true)
        if(!isMetaManager) {
          stop()
        }
        Success(())
      } else if (localErrorCount.get() > awsCompotaConfiguration.localErrorThreshold) {
        sendUnDeployCommand("reached error threshold for instance " + instanceId.id, force = true)
        if(!isMetaManager) {
          stop()
        }
        Success(())
      } else {
        errorTable.reportError(namespace, System.currentTimeMillis(), instanceId, t.toString, stackTrace.toString())
      }
    }.recover { case tt => {
        //something really bad happened
        val message = new StringBuilder()
        logger.printThrowable(t, {s => message.append(s + System.lineSeparator())})
        sendUnDeployCommand(AwsErrorTable.errorTableError + message.toString(), force = true)
        if(!isMetaManager) {
          stop()
        }
      }
    }
    ()
  }
}


