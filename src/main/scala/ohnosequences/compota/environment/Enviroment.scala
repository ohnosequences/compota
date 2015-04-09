package ohnosequences.compota.environment

import ohnosequences.logging.Logger

import java.io.File
import scala.util.Failure

/**
 * where worker lives
 */
case class InstanceId(id: String)

abstract class AnyEnvironment {

  def start(): Unit

  def instanceId: InstanceId

  val logger: Logger

  def kill()

  def fatalError(failure: Throwable): Unit = {
    logger.error("fatal error")
    logger.error(failure)
    kill()
  }

  def isTerminated: Boolean

  //todo: all repeats are here
  def reportError(taskId: String, t: Throwable)

  val workingDirectory: File
}

//trait Environment[C] extends AnyEnvironment {
//
//  type QueueContext = C
//}
