package ohnosequences.compota.tasks

import ohnosequences.compota.NisperoAux


object Naming {
  def generateTasks[O](nispero: NisperoAux, inputId: String, output: List[O]): List[(String, O)] = {
    output.zipWithIndex.map { case (o, i) =>
      (inputId + "." + nispero.name + "." + i, o)
    }
  }

}
