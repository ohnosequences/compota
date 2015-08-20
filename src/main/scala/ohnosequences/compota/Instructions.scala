package ohnosequences.compota

import java.io.File

import ohnosequences.logging.Logger

import scala.util.Try

trait InstructionsAux {

  type I

  type O

  type Context

  def solve(logger: Logger, workingDirectory: File, context: Context, input: I): Try[List[O]]

  def prepare(logger: Logger, workingDirectory: File, aws: AWS): Try[Context]
}

trait Instructions[Input, Output] extends InstructionsAux {

  type I = Input

  type O = Output

}

/**
 * Instructions that returns only one message
 */
abstract class MapInstructions[Input, Output] extends Instructions[Input, Output] {

  def solve(logger: Logger, workingDirectory: File, context: Context, input: I): Try[List[Output]] = {
    apply(logger, workingDirectory, context, input).map(List(_))
  }

  def apply(logger: Logger, workingDirectory: File, context: Context, input: Input): Try[Output]

}

