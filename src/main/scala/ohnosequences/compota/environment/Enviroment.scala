package ohnosequences.compota.environment

import java.util.concurrent.ExecutorService

import ohnosequences.compota.{ErrorTable, Namespace}
import ohnosequences.logging.Logger

import java.io.File
import scala.concurrent.ExecutionContext
import scala.util.{Try}


case class InstanceId(id: String)

trait Env {

  val logger: Logger

  def isStopped: Boolean

  val workingDirectory: File

}

abstract class AnyEnvironment[E <: AnyEnvironment[E]] extends Env {

  def subEnvironmentSync[R](suffix: String)(statement: E => R): Try[(E, R)]

  def subEnvironment(suffix: String)(statement: E => Unit): Try[E]

  def instanceId: InstanceId

  val errorTable: ErrorTable

  val executor: ExecutorService

  def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit]

  def stop(): Unit

  /**
   * all repeats are here, "5 errors - reboot,  10 errors - undeplot compota
   * shouldn't do nothing if env is terminated
   */
  def reportError(nameSpace: Namespace, t: Throwable)



}

