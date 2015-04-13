package ohnosequences.compota.aws

import java.io.File

import ohnosequences.awstools.AWSClients
import ohnosequences.compota.Namespace
import ohnosequences.compota.aws.deployment.Metadata
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.environment.{InstanceId, AnyEnvironment}
import ohnosequences.logging.Logger

import scala.util.Try


class AwsEnvironment(val awsClients: AWSClients, val metadata: Metadata, val workingDirectory: File) extends AnyEnvironment {


  override def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit] = ???


  def toDynamoDBContext: DynamoDBContext = {
    DynamoDBContext(awsClients, metadata, logger)
  }

  override def instanceId: InstanceId = ???

  override def isTerminated: Boolean = ???

  override def stop(): Unit = ???

  //todo: all repeats are here
  override def reportError(namespace: Namespace, t: Throwable): Unit = ???

  override val logger: Logger = ???
}
