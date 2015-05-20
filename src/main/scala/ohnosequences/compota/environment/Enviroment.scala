package ohnosequences.compota.environment

import java.util.concurrent.ExecutorService

import ohnosequences.compota.{ErrorTable, Namespace}
import ohnosequences.logging.Logger

import java.io.File
import scala.concurrent.ExecutionContext
import scala.util.{Try}


case class InstanceId(id: String)

abstract class AnyEnvironment[E <: AnyEnvironment[E]] {

  def subEnvironmentSync[R](suffix: String)(statement: E => R): Try[(E, R)]

  def subEnvironment(suffix: String)(statement: E => Unit): Try[E]

  def instanceId: InstanceId

  val logger: Logger

  val errorTable: ErrorTable

  val executor: ExecutorService

  def terminate(): Unit

  def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit]

  def isStopped: Boolean

  def stop(): Unit

  /**
   * all repeats are here, "5 errors - reboot,  10 errors - undeplot compota
   * shouldn't do nothing if env is terminated
   */
  def reportError(nameSpace: Namespace, t: Throwable)

  val workingDirectory: File


}

