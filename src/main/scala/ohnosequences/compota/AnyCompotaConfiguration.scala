package ohnosequences.compota

import scala.concurrent.duration.Duration

trait AnyCompotaConfiguration {
  val timeout: Duration
  val loggerDebug: Boolean
  val terminationDaemonIdleTime: Duration
  val deleteErrorQueue: Boolean
}
