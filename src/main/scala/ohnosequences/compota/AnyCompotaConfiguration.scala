package ohnosequences.compota

import scala.concurrent.duration.Duration
import scala.concurrent.duration._

trait AnyCompotaConfiguration {
  val name: String
  val timeout: Duration
  val loggerDebug: Boolean
  val terminationDaemonIdleTime: Duration = Duration(100, SECONDS)
  val deleteErrorQueue: Boolean
  val consolePassword: String = "nispero"
}

trait AnyNisperoConfiguration {
  val name: String
}
