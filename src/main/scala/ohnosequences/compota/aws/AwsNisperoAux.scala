package ohnosequences.compota.aws

import ohnosequences.compota.worker.Worker
import ohnosequences.compota.{Nispero, Instructions, NisperoAux}
import ohnosequences.compota.queues.Queue


trait AwsNisperoAux extends NisperoAux {
  val configuration: AwsNisperoConfigurationAux

  type QueueContext = AWS

}




class AwsNispero[In, Out, InQueue <: Queue[In, AWS], OutQueue <: Queue[Out, AWS]](
                                                                      name: String,
                                                                      inputQueue: InQueue,
                                                                      outputQueue: OutQueue,
                                                                      instructions: Instructions[In, Out],
                                                                      val configuration: AwsNisperoConfigurationAux)
  extends Nispero[In, Out, AWS, InQueue, OutQueue](name, inputQueue, outputQueue, instructions) with AwsNisperoAux {



}


