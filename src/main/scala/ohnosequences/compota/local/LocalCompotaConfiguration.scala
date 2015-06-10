package ohnosequences.compota.local

import java.io.File

import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.{Namespace, AnyCompotaConfiguration}

import scala.concurrent.duration.Duration
import scala.concurrent.duration.MINUTES

trait AnyLocalCompotaConfiguration extends AnyCompotaConfiguration {

  val initialEnvironmentId = InstanceId("local")

  def workingDirectory: File = new File("compota")
  def loggingDirectory: File = new File(workingDirectory, "logs")


  def taskLogDirectory(namespace: Namespace): File = {
    new File(loggingDirectory, namespace.toString)
  }

  def taskLogFile(namespace: Namespace): File = {
    new File(taskLogDirectory(namespace), "log.txt")
  }

  def errorThreshold = 5


  override def localErrorThreshold: Int = 15

  override def deleteErrorQueue: Boolean = true

  def visibilityTimeout: Duration
}


case class LocalCompotaConfiguration(name: String, loggerDebug: Boolean, timeout: Duration, visibilityTimeout: Duration = Duration(10, MINUTES)) extends AnyLocalCompotaConfiguration {

}