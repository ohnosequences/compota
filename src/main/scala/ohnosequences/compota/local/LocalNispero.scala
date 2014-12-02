package ohnosequences.compota.local

import ohnosequences.compota.{AnyNispero, Nispero, Instructions}
import ohnosequences.compota.queues.Queue

trait LocalNisperoAux extends AnyNispero {
 // val configuration: AwsNisperoConfigurationAux

  override type QueueCtx = Unit

}




class LocalNispero[In, Out, InQueue <: Queue[In, Unit], OutQueue <: Queue[Out, Unit]](
                                                                                   name: String,
                                                                                   inputQueue: InQueue,
                                                                                   outputQueue: OutQueue,
                                                                                   instructions: Instructions[In, Out])
  extends Nispero[In, Out, Unit, InQueue, OutQueue](name, inputQueue, outputQueue, instructions) with LocalNisperoAux {


}
