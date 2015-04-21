package ohnosequences.compota.local

import ohnosequences.compota.{AnyNispero, Nispero, Instructions}
import ohnosequences.compota.queues.Queue

trait AnyLocalNispero extends AnyNispero {
 // val configuration: AwsNisperoConfigurationAux

  override type NisperoEnvironment = LocalEnvironment

  val workers: Int

}


class LocalNispero[In, Out, InContext, OutContext, InQueue <: Queue[In, InContext], OutQueue <: Queue[Out, OutContext]](
                                                                                   inputQueue: InQueue,
                                                                                   inContext: LocalEnvironment => InContext,
                                                                                   outputQueue: OutQueue,
                                                                                   outContext: LocalEnvironment => OutContext,
                                                                                   instructions: Instructions[In, Out],
                                                                                   val workers: Int)
  extends Nispero[In, Out, LocalEnvironment, InContext, OutContext, InQueue, OutQueue](inputQueue, inContext, outputQueue, outContext, instructions) with AnyLocalNispero {

}

class LocalNisperoLocal[In, Out, InQueue <: Queue[In, LocalContext], OutQueue <: Queue[Out, LocalContext]](
                                                                                                                         inputQueue: InQueue,
                                                                                                                         outputQueue: OutQueue,
                                                                                                                         instructions: Instructions[In, Out],
                                                                                                                         workers: Int)
  extends LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue](inputQueue, {e: LocalEnvironment => e.localContext}, outputQueue, {e: LocalEnvironment => e.localContext}, instructions, workers) with AnyLocalNispero {

}


object LocalNispero {
  def apply[In, Out, InQueue <: Queue[In, LocalContext], OutQueue <: Queue[Out, LocalContext]](
                                                                                 inputQueue: InQueue,
                                                                                 outputQueue: OutQueue,
                                                                                 instructions: Instructions[In, Out],
                                                                                 worker: Int):
  LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue] = new LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue](
  inputQueue, {e: LocalEnvironment => e.localContext},
  outputQueue, {e: LocalEnvironment => e.localContext},
  instructions,
  worker)
}



