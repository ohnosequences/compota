package ohnosequences.compota.local

import ohnosequences.compota.{Nispero, Instructions, NisperoAux}
import ohnosequences.compota.aws.{AWS, AwsNisperoConfigurationAux}
import ohnosequences.compota.queues.Queue

trait LocalNisperoAux extends NisperoAux {
 // val configuration: AwsNisperoConfigurationAux

  override type QCtxCtx = Unit

}




class LocalNispero[In, Out, InQueue <: Queue[In, Unit], OutQueue <: Queue[Out, Unit]](
                                                                                   name: String,
                                                                                   inputQueue: InQueue,
                                                                                   outputQueue: OutQueue,
                                                                                   instructions: Instructions[In, Out])
  extends Nispero[In, Out, Unit, InQueue, OutQueue](name, inputQueue, outputQueue, instructions) with LocalNisperoAux {



}
