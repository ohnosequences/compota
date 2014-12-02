package ohnosequences.compota.aws

// import ohnosequences.compota.worker.Worker
import ohnosequences.compota._
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.queues._


trait AwsNisperoAux extends AnyNispero {
  val configuration: AwsNisperoConfigurationAux

  type QueueContext = DynamoDBContext

}




class AwsNispero[In, Out, InQueue <: Queue[In, DynamoDBContext], OutQueue <: Queue[Out, DynamoDBContext]](
                                                                      name: String,
                                                                      inputQueue: InQueue,
                                                                      outputQueue: OutQueue,
                                                                      instructions: Instructions[In, Out],
                                                                      val configuration: AwsNisperoConfigurationAux)
  extends Nispero[In, Out, DynamoDBContext, InQueue, OutQueue](name, inputQueue, outputQueue, instructions) with AwsNisperoAux {



}


