package ohnosequences.compota.local

import ohnosequences.compota.{AnyNispero, Nispero, Instructions}
import ohnosequences.compota.queues.Queue

trait AnyLocalNispero extends AnyNispero {
 // val configuration: AwsNisperoConfigurationAux

  override type NisperoEnvironment = ThreadEnvironment

  val workers: Int

}


class LocalNispero[In, Out, InContext, OutContext, InQueue <: Queue[In, InContext], OutQueue <: Queue[Out, OutContext]](
                                                                                   inputQueue: InQueue,
                                                                                   inContext: ThreadEnvironment => InContext,
                                                                                   outputQueue: OutQueue,
                                                                                   outContext: ThreadEnvironment => OutContext,
                                                                                   instructions: Instructions[In, Out],
                                                                                   val workers: Int)
  extends Nispero[In, Out, ThreadEnvironment, InContext, OutContext, InQueue, OutQueue](inputQueue, inContext, outputQueue, outContext, instructions) with AnyLocalNispero {

}


object LocalNispero {
  def apply[In, Out, InQueue <: Queue[In, Unit], OutQueue <: Queue[Out, Unit]](
                                                                                 inputQueue: InQueue,
                                                                                 outputQueue: OutQueue,
                                                                                 instructions: Instructions[In, Out],
                                                                                 worker: Int):
  LocalNispero[In, Out, Unit, Unit, InQueue, OutQueue] = new LocalNispero[In, Out, Unit, Unit, InQueue, OutQueue](
  inputQueue, {t: ThreadEnvironment => ()},
  outputQueue, {t: ThreadEnvironment => ()},
  instructions,
  worker)
}



