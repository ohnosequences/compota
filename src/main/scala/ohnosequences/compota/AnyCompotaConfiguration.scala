package ohnosequences.compota

import ohnosequences.compota.environment.InstanceId

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

trait AnyCompotaConfiguration {

  val initialEnvironmentId = InstanceId("local")

  def name: String
  def timeout: Duration
  def loggerDebug: Boolean
  def terminationDaemonIdleTime: Duration = Duration(100, SECONDS)
  def deleteErrorQueue: Boolean
  def consolePassword: String = "nispero"
  def consoleHTTPS: Boolean = false
  def consoleInstancePageSize = 10
  def consoleMessagePageSize = 10
  def consoleErrorsPageSize = 10


  def localErrorThreshold: Int
  def errorThreshold: Int

  val loggersPrintToConsole: Boolean = true

}

trait AnyNisperoConfiguration {
  def name: String
  def workers: Int
}
