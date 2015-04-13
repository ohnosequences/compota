package ohnosequences.compota.environment

import ohnosequences.compota.Namespace
import ohnosequences.logging.Logger

import java.io.File
import scala.util.{Try}


case class InstanceId(id: String)

abstract class AnyEnvironment {

  def instanceId: InstanceId

  val logger: Logger

  def stop()

  def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit]

  def isTerminated: Boolean

  /**
   * all repeats are here, "5 errors - reboot,  10 errors - undeplot compota
   * shouldn't do nothing if env is terminated
   */
  def reportError(nameSpace: Namespace, t: Throwable)

  val workingDirectory: File


}

