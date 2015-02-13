package ohnosequences.compota

import ohnosequences.logging.Logger

import scala.util.Try

//can't return an error?
trait AnyInstructions {

  type Input
  type Output
  type Context

  def prepare(logger: Logger): Try[Context]

  def solve(logger: Logger, context: Context, input: Input): Try[List[Output]]

  val name: String

}

trait Instructions[I, O] extends AnyInstructions {



  type Input = I
  type Output = O

}

//results with one message
abstract class MapInstructions[I, O] extends Instructions[I, O] {

  //val arity = 1

  final def solve(logger: Logger, context: Context, input: Input): Try[List[Output]] = {

    //println(apply(logger, context, input).map(List(_)))
    apply(logger, context, input).map(List(_))
  }

  def apply(logger: Logger, context: Context, input: Input): Try[Output]

}

