package ohnosequences.compota

import ohnosequences.compota.environment.{Env}
import ohnosequences.logging.Logger

import scala.util.Try

trait AnyInstructions {

  type Input
  type Output
  type Context

  def prepare(env: Env): Try[Context]

  def solve(env: Env, context: Context, input: Input): Try[List[Output]]

}

trait Instructions[I, O] extends AnyInstructions {

  type Input = I
  type Output = O

}

//results with one message
abstract class MapInstructions[I, O] extends Instructions[I, O] {

  //val arity = 1

  final def solve(env: Env, context: Context, input: Input): Try[List[Output]] = {

    //println(apply(logger, context, input).map(List(_)))
    apply(env, context, input).map(List(_))
  }

  def apply(env: Env, context: Context, input: Input): Try[Output]

}

