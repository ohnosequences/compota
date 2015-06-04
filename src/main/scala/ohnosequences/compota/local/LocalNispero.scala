package ohnosequences.compota.local

import ohnosequences.compota.{AnyNisperoConfiguration, AnyNispero, Instructions}
import ohnosequences.compota.queues.{AnyQueue, Queue}

trait AnyLocalNispero extends AnyNispero {

  override type NisperoEnvironment = LocalEnvironment

  override type NisperoConfiguration = AnyLocalNisperoConfiguration
}


class LocalNispero[In, Out, InContext, OutContext, InQueue <: AnyQueue.of2[In, InContext], OutQueue <: AnyQueue.of2[Out, OutContext]](
                                                                                   val inputQueue: InQueue,
                                                                                   val inputContext: LocalEnvironment => InContext,
                                                                                   val outputQueue: OutQueue,
                                                                                   val outputContext: LocalEnvironment => OutContext,
                                                                                   val instructions: Instructions[In, Out],
                                                                                   val configuration: AnyLocalNisperoConfiguration)
  extends AnyLocalNispero {

  override type NisperoInput = In
  override type NisperoOutput = Out

  override type NisperoInputContext = InContext
  override type NisperoOutputContext = OutContext


  override type NisperoInputQueue = InQueue
  override type NisperoOutputQueue = OutQueue
}

class LocalNisperoLocal[In, Out, InQueue <: AnyQueue.of2[In, LocalContext], OutQueue <: AnyQueue.of2[Out, LocalContext]](
                                                                                                                         inputQueue: InQueue,
                                                                                                                         outputQueue: OutQueue,
                                                                                                                         instructions: Instructions[In, Out],
                                                                                                                         localConfiguration: AnyLocalNisperoConfiguration
                                                                                                            )
  extends LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue](inputQueue, {e: LocalEnvironment => e.localContext}, outputQueue, {e: LocalEnvironment => e.localContext}, instructions, localConfiguration) with AnyLocalNispero {

}


object LocalNispero {
  def apply[In, Out, InQueue <: AnyQueue.of2[In, LocalContext], OutQueue <: AnyQueue.of2[Out, LocalContext]](
                                                                                 inputQueue: InQueue,
                                                                                 outputQueue: OutQueue,
                                                                                 instructions: Instructions[In, Out],
                                                                                 localConfiguration: AnyLocalNisperoConfiguration):
  LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue] = new LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue](
  inputQueue, {e: LocalEnvironment => e.localContext},
  outputQueue, {e: LocalEnvironment => e.localContext},
  instructions,
  localConfiguration)
}



