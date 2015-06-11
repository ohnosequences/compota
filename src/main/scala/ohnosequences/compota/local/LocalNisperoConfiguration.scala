package ohnosequences.compota.local

import java.io.File

import ohnosequences.compota.AnyNisperoConfiguration

trait AnyLocalNisperoConfiguration extends AnyNisperoConfiguration {
  def compotaConfiguration: AnyLocalCompotaConfiguration
  def workingDirectory = new File(compotaConfiguration.workingDirectory, name)
}

case class LocalNisperoConfiguration(compotaConfiguration: AnyLocalCompotaConfiguration, name: String, workers: Int) extends AnyLocalNisperoConfiguration


