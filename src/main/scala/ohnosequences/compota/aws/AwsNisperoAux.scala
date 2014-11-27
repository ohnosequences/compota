package ohnosequences.compota.aws

import ohnosequences.compota.worker.Worker
import ohnosequences.compota.{Nispero, Instructions, NisperoAux}
import ohnosequences.compota.queues.Queue


trait AwsNisperoAux extends NisperoAux {
  val awsConfiguration: AwsStuff

}




class AwsNispero[In, Out, InQueue <: Queue[In], OutQueue <: Queue[Out]](
                                                                      name: String,
                                                                      inputQueue: InQueue,
                                                                      outputQueue: OutQueue,
                                                                      instructions: Instructions[In, Out],
                                                                      val awsConfiguration: AwsStuff)
  extends Nispero(name, inputQueue, outputQueue, instructions) with AwsNisperoAux {



}



case class AwsStuff(s: String)