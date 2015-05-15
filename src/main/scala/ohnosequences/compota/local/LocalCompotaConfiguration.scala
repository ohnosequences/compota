package ohnosequences.compota.local

import java.io.File

import ohnosequences.compota.{Namespace, AnyCompotaConfiguration}

import scala.concurrent.duration.Duration

abstract class LocalCompotaConfiguration extends AnyCompotaConfiguration {

  def workingDirectory: File = new File("compota")
  def loggingDirectory: File = new File(workingDirectory, "logs")

  def taskLogDirectory(namespace: Namespace): File = {
    new File(loggingDirectory, namespace.toString)
  }

  def taskLogFile(namespace: Namespace): File = {
    new File(taskLogDirectory(namespace), "log.txt")
  }


  val errorThreshold = 5
  override val deleteErrorQueue: Boolean = true
}
