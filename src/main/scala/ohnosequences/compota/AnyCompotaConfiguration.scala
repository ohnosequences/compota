package ohnosequences.compota

import ohnosequences.awstools.dynamodb.RepeatConfiguration
import ohnosequences.compota.environment.InstanceId

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

trait AnyCompotaConfiguration {

  val initialEnvironmentId = InstanceId("local")

  def name: String
  def timeout: Duration
  def loggerDebug: Boolean = false
  def terminationDaemonIdleTime: Duration = Duration(100, SECONDS)
  def deleteErrorQueue: Boolean = true
  def consolePassword: String = "compota"
  def consoleHTTPS: Boolean = false
  def consolePort: Int = 9000
  def consoleInstancePageSize = 10
  def consoleMessagePageSize = 10
  def consoleErrorsPageSize = 10


  def localErrorThreshold: Int
  def globalErrorThreshold: Int

  val loggersPrintToConsole: Boolean = true

  def environmentRepeatConfiguration: RepeatConfiguration

}

trait AnyNisperoConfiguration {
  def name: String
  def workers: Int
}
