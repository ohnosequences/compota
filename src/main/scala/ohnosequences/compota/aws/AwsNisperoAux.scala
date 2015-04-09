package ohnosequences.compota.aws

// import ohnosequences.compota.worker.Worker
import ohnosequences.compota._
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.queues._


trait AwsNisperoAux extends AnyNispero {
  val configuration: AwsNisperoConfigurationAux

  override type NisperoEnvironment = AwsEnvironment

}




class AwsNispero[In, Out, InCtx, OutCtx, InQueue <: Queue[In, InCtx], OutQueue <: Queue[Out, OutCtx]](
                                                                      inputQueue: InQueue,
                                                                      inputContext: AwsEnvironment => InCtx,
                                                                      outputQueue: OutQueue,
                                                                      outContext: AwsEnvironment => OutCtx,
                                                                      instructions: Instructions[In, Out],
                                                                      val configuration: AwsNisperoConfigurationAux)
  extends Nispero[In, Out, AwsEnvironment, InCtx, OutCtx, InQueue, OutQueue](inputQueue, inputContext, outputQueue, outContext, instructions) with AwsNisperoAux {



}


