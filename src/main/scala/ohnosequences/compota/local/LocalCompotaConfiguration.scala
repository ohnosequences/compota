package ohnosequences.compota.local

import java.io.File

abstract class LocalCompotaConfiguration {
  val loggerDebug: Boolean
  val workingDirectory: File = new File("compota")
  val errorThreshold = 5

}
