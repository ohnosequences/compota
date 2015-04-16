package ohnosequences.compota.aws

import java.io.File
import java.util.concurrent.ExecutorService
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


class AwsEnvironment(val awsClients: AWSClients,
                     val awsCompotaConfiguration: AwsCompotaConfiguration,
                     val logger0: Logger,
                     val workingDirectory: File,
                     val awsInstanceId: String,
                     val errorTable: ErrorTable,
                     val sendUnDeployCommand0: (AwsEnvironment, String, Boolean) => Try[Unit],
                     val isMetaManager: Boolean
                      ) extends AnyEnvironment { awsEnvironment =>


  override val executor: ExecutorService = ???

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


  override def terminate(): Unit = {
    stop()
    awsClients.ec2.terminateInstance(awsInstanceId)
  }

  override def stop(): Unit ={
    isTerminatedFlag.set(true)
  }

  override def reportError(namespace: Namespace, t: Throwable): Unit = {

    logger.error(t)
    val message = new StringBuilder()
    logger.printThrowable(t, {s => message.append(s + System.lineSeparator())})

    errorTable.getNameSpaceErrorsCount(namespace).flatMap { globalCount =>
      if (globalCount > awsCompotaConfiguration.globalErrorThresholdPerNameSpace) {
        sendUnDeployCommand("reached error threshold for " + namespace.toString, force = true)
        if(!isMetaManager) {
          terminate()
        }
        Success(())
      } else if (localErrorCount.get() > awsCompotaConfiguration.localErrorThreshold) {
        sendUnDeployCommand("reached error threshold for instance " + instanceId.id, force = true)
        if(!isMetaManager) {
          terminate()
        }
        Success(())
      } else {
        errorTable.fail(namespace, instanceId, message.toString())
      }
    }.recover { case tt => {
        //something really bad happened
        val message = new StringBuilder()
        logger.printThrowable(t, {s => message.append(s + System.lineSeparator())})
        sendUnDeployCommand(ErrorTable.errorTableError + message.toString(), force = true)
        if(!isMetaManager) {
          stop()
        }
      }
    }
    ()
  }

  override val logger: Logger = logger0
}


