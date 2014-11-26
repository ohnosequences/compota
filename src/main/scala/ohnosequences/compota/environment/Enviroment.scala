package ohnosequences.compota.environment

import ohnosequences.compota.logging.Logger

import scala.util.Failure

/**
 * where worker lives
 */
case class InstanceId(id: String)

trait Environment {
  def instanceId: InstanceId
  val logger: Logger

  def kill()

  def fatalError(failure: Throwable): Unit = {
    logger.error("fatal error")
    logger.error(failure)
    kill()
  }
  def isTerminated: Boolean
  def reportError(taskId: String, t: Throwable)

}