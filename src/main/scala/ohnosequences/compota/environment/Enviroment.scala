package ohnosequences.compota.environment

import ohnosequences.logging.Logger

import java.io.File
import scala.annotation.tailrec
import scala.util.{Success, Try, Failure}

/**
 * where worker lives
 */
case class InstanceId(id: String)

abstract class AnyEnvironment {

  def start(): Unit

  def instanceId: InstanceId

  val logger: Logger

  def stop()

  def fatalError(nameSpace: String, failure: Throwable): Unit = {
    logger.error("fatal error")
    reportError(nameSpace, failure)
    stop()
  }

  def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit]

  def isTerminated: Boolean

  //todo: all repeats are here
  def reportError(nameSpace: String, t: Throwable)

  val workingDirectory: File

  def repeat[T](actionName: String, attempts: Int, timeout: Option[Long] = None)(action: =>Try[T]): Try[T] = {
    @tailrec
    def repeatRec(attemptsLeft: Int, lastRes: Try[T], timeout: Option[Long]): Try[T] = {
      if (attemptsLeft < 1) {
        logger.error(actionName + " failed " + attempts + " times")
        lastRes
      } else {
        action match {
          case Success(s) => Success(s)
          case Failure(t) => {
            logger.warn(actionName + " failed, " + attemptsLeft + " attempts left")
            logger.debug(t)
            timeout.foreach{ t => logger.info("sleep " + t + "ms"); Thread.sleep(t) }

            repeatRec(attemptsLeft - 1, Failure(t), timeout.map(_ * 2))
          }
        }
      }
    }

    repeatRec(attempts, Failure(new Error("have not been launched")), timeout)
  }

}



//trait Environment[C] extends AnyEnvironment {
//
//  type QueueContext = C
//}
