package ohnosequences.compota

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

trait AnyCompotaConfiguration {
  def name: String
  def timeout: Duration
  def loggerDebug: Boolean
  def terminationDaemonIdleTime: Duration = Duration(100, SECONDS)
  def deleteErrorQueue: Boolean
  def consolePassword: String = "nispero"
}

trait AnyNisperoConfiguration {
  def name: String
}
