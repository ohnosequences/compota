package ohnosequences.compota.environment

import ohnosequences.logging.Logger

import scala.util.Failure

/**
 * where worker lives
 */
case class InstanceId(id: String)

trait AnyEnvironment {

  type Context
  def instanceId: InstanceId
  val logger: Logger

  // TODO: what's this?
  def queueCtx: Context

  def kill()

  def fatalError(failure: Throwable): Unit = {
    logger.error("fatal error")
    logger.error(failure)
    kill()
  }
  def isTerminated: Boolean
  def reportError(taskId: String, t: Throwable)
}

trait Environment[C] extends AnyEnvironment {

  type Context = C
}
