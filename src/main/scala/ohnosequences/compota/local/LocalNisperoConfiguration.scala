package ohnosequences.compota.local

import java.io.File

import ohnosequences.compota.AnyNisperoConfiguration

class LocalNisperoConfiguration(val name: String, val workers: Int, val workingDirectory: File = new File(".")) extends AnyNisperoConfiguration {

}
