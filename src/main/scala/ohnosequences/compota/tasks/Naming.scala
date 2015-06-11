package ohnosequences.compota.tasks

import ohnosequences.compota.AnyNispero


object Naming {
  def generateTasks[O](nispero: AnyNispero, inputId: String, output: List[O]): List[(String, O)] = {
    output.zipWithIndex.map { case (o, i) =>
      (inputId + "." + nispero.configuration.name + "." + (i + 1), o)
    }
  }

}
