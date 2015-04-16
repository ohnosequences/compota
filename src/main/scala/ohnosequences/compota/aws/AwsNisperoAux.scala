package ohnosequences.compota.aws

// import ohnosequences.compota.worker.Worker
import ohnosequences.compota._
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.queues._


trait AnyAwsNispero extends AnyNispero {
  val configuration: AwsNisperoConfiguration

  override type NisperoEnvironment = AwsEnvironment

}




class AwsNispero[In, Out, InCtx, OutCtx, InQueue <: Queue[In, InCtx], OutQueue <: Queue[Out, OutCtx]](
                                                                      inputQueue: InQueue,
                                                                      inputContext: AwsEnvironment => InCtx,
                                                                      outputQueue: OutQueue,
                                                                      outContext: AwsEnvironment => OutCtx,
                                                                      instructions: Instructions[In, Out],
                                                                      val configuration: AwsNisperoConfiguration)
  extends Nispero[In, Out, AwsEnvironment, InCtx, OutCtx, InQueue, OutQueue](inputQueue, inputContext, outputQueue, outContext, instructions) with AnyAwsNispero {



}

object AwsNispero {
  def apply[In, Out, InQueue <: Queue[In, DynamoDBContext], OutQueue <: Queue[Out, DynamoDBContext]](
                                                                                inputQueue: InQueue,
                                                                                outputQueue: OutQueue,
                                                                                instructions: Instructions[In, Out],
                                                                                configuration: AwsNisperoConfiguration):
  AwsNispero[In, Out, DynamoDBContext, DynamoDBContext, InQueue, OutQueue] = new AwsNispero[In, Out, DynamoDBContext, DynamoDBContext, InQueue, OutQueue](
  inputQueue, {t: AwsEnvironment => t.createDynamoDBContext()},
  outputQueue,{t: AwsEnvironment => t.createDynamoDBContext()},
  instructions,
  configuration)
}


