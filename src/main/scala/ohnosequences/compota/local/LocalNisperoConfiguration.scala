package ohnosequences.compota.local

import java.io.File

import ohnosequences.compota.AnyNisperoConfiguration

trait LocalNisperoConfiguration extends AnyNisperoConfiguration {

  val workers: Int

  def workingDirectory: File


}
