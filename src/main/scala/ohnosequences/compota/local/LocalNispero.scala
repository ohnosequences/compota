package ohnosequences.compota.local

import ohnosequences.compota.{AnyNispero, Nispero, Instructions}
import ohnosequences.compota.queues.{AnyQueue, Queue}

trait AnyLocalNispero extends AnyNispero {

  val localConfiguration: LocalNisperoConfiguration

  override type NisperoEnvironment = LocalEnvironment


}


class LocalNispero[In, Out, InContext, OutContext, InQueue <: AnyQueue.of2[In, InContext], OutQueue <: AnyQueue.of2[Out, OutContext]](
                                                                                   inputQueue: InQueue,
                                                                                   inContext: LocalEnvironment => InContext,
                                                                                   outputQueue: OutQueue,
                                                                                   outContext: LocalEnvironment => OutContext,
                                                                                   instructions: Instructions[In, Out],
                                                                                   val localConfiguration: LocalNisperoConfiguration)
  extends Nispero[In, Out, LocalEnvironment, InContext, OutContext, InQueue, OutQueue](
    inputQueue,
    inContext,
    outputQueue,
    outContext,
    instructions,
    localConfiguration) with AnyLocalNispero {

}

class LocalNisperoLocal[In, Out, InQueue <: AnyQueue.of2[In, LocalContext], OutQueue <: AnyQueue.of2[Out, LocalContext]](
                                                                                                                         inputQueue: InQueue,
                                                                                                                         outputQueue: OutQueue,
                                                                                                                         instructions: Instructions[In, Out],
                                                                                                                         localConfiguration: LocalNisperoConfiguration
                                                                                                            )
  extends LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue](inputQueue, {e: LocalEnvironment => e.localContext}, outputQueue, {e: LocalEnvironment => e.localContext}, instructions, localConfiguration) with AnyLocalNispero {

}


object LocalNispero {
  def apply[In, Out, InQueue <: AnyQueue.of2[In, LocalContext], OutQueue <: AnyQueue.of2[Out, LocalContext]](
                                                                                 inputQueue: InQueue,
                                                                                 outputQueue: OutQueue,
                                                                                 instructions: Instructions[In, Out],
                                                                                 localConfiguration: LocalNisperoConfiguration):
  LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue] = new LocalNispero[In, Out, LocalContext, LocalContext, InQueue, OutQueue](
  inputQueue, {e: LocalEnvironment => e.localContext},
  outputQueue, {e: LocalEnvironment => e.localContext},
  instructions,
  localConfiguration)
}



