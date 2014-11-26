package ohnosequences.compota.enviroment

import ohnosequences.compota.logging.Logger

import scala.util.Failure

/**
 * where worker lives
 */
case class InstanceId(id: String)
trait Instance {
  def getId: InstanceId
  def getLogger: Logger
  def fatalError(failure: Throwable)
  def kill() //when fatal error occurs
  def isTerminated: Boolean

}
