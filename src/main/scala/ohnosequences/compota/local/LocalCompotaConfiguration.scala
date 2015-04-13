package ohnosequences.compota.local

import java.io.File

import ohnosequences.compota.AnyCompotaConfiguration

import scala.concurrent.duration.Duration

abstract class LocalCompotaConfiguration extends AnyCompotaConfiguration {

  val workingDirectory: File = new File("compota")
  val errorThreshold = 5
  override val deleteErrorQueue: Boolean = true
}
