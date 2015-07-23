package ohnosequences.compota.local

import java.io.File

import ohnosequences.awstools.dynamodb.RepeatConfiguration
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.{Namespace, AnyCompotaConfiguration}

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

trait AnyLocalCompotaConfiguration extends AnyCompotaConfiguration {

  def workingDirectory: File = new File("compota")
  def loggingDirectory: File = new File(workingDirectory, "logs")


  def taskLogDirectory(namespace: Namespace): File = {
    new File(loggingDirectory, namespace.toString)
  }

  def taskLogFile(namespace: Namespace): File = {
    new File(taskLogDirectory(namespace), "log.txt")
  }


  override def consoleHTTPS: Boolean = false

  override def consolePort: Int = 8000

  override def globalErrorThreshold: Int = 5

  val consoleNamespacesPageSize = 10

  override def localErrorThreshold: Int = 15

  override def deleteErrorQueue: Boolean = true

  def visibilityTimeout: Duration

  override def environmentRepeatConfiguration: RepeatConfiguration = RepeatConfiguration(
    attemptThreshold = 100,
    initialTimeout = Duration(1, SECONDS),
    timeoutThreshold = Duration(1, MINUTES),
    coefficient = 1.2
  )
}


case class LocalCompotaConfiguration(name: String, loggerDebug: Boolean, timeout: Duration, visibilityTimeout: Duration = Duration(10, MINUTES)) extends AnyLocalCompotaConfiguration {

}