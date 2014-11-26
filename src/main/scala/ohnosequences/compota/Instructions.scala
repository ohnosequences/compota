package ohnosequences.compota

import ohnosequences.compota.logging.Logger

import scala.util.Try

//can't return an error?
trait InstructionsAux {

  type I
  type O
  type Context

  def prepare(logger: Logger): Context

  def solve(logger: Logger, context: Context, input: I): Try[List[O]]

}

trait Instructions[Input, Output] extends InstructionsAux {

  type I = Input
  type O = Output

}

//results with one message
abstract class MapInstructions[Input, Output] extends Instructions[Input, Output] {

  //val arity = 1

  def solve(logger: Logger, context: Context, input: I): Try[List[Output]] = {

    //println(apply(logger, context, input).map(List(_)))
    apply(logger, context, input).map(List(_))
  }

  def apply(logger: Logger, context: Context, input: I): Try[O]

}

