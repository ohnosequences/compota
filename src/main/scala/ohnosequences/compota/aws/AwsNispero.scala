package ohnosequences.compota.aws

import ohnosequences.compota._
import ohnosequences.compota.aws.queues.DynamoDBContext
import ohnosequences.compota.local.{LocalNisperoConfiguration, LocalEnvironment}
import ohnosequences.compota.queues._


trait AnyAwsNispero extends AnyNispero {
  override type NisperoConfiguration = AwsNisperoConfiguration
  override type NisperoEnvironment = AwsEnvironment
}

//object AnyAwsNispero {
//  type of[N <: ]
//}




class AwsNisperoCustomContext[In, Out, InCtx, OutCtx, InQueue <:  AnyQueue.of2[In, InCtx], OutQueue <: AnyQueue.of2[Out, OutCtx]](
                                                                      val inputQueue: InQueue,
                                                                      val inputContext: AwsEnvironment => InCtx,
                                                                      val outputQueue: OutQueue,
                                                                      val outputContext: AwsEnvironment => OutCtx,
                                                                      val instructions: Instructions[In, Out],
                                                                      val configuration: AwsNisperoConfiguration)
  extends AnyAwsNispero {

  override type NisperoConfiguration = AwsNisperoConfiguration

  override type NisperoInput = In
  override type NisperoOutput = Out

  override type NisperoInputContext = InCtx
  override type NisperoOutputContext = OutCtx

  override type NisperoInputQueue = InQueue
  override type NisperoOutputQueue = OutQueue
}

class AwsNispero[In, Out, InQueue <:  AnyQueue.of2[In, DynamoDBContext], OutQueue <: AnyQueue.of2[Out, DynamoDBContext]](
                                                                                                                                   val inputQueue: InQueue,
                                                                                                                                   val outputQueue: OutQueue,
                                                                                                                                   val instructions: Instructions[In, Out],
                                                                                                                                   val configuration: AwsNisperoConfiguration)
  extends AnyAwsNispero {

  override type NisperoConfiguration = AwsNisperoConfiguration

  override type NisperoInput = In
  override type NisperoOutput = Out

  override type NisperoInputContext = DynamoDBContext
  override type NisperoOutputContext = DynamoDBContext

  override type NisperoInputQueue = InQueue
  override type NisperoOutputQueue = OutQueue

  override def inputContext: (AwsEnvironment) => DynamoDBContext = {e => e.createDynamoDBContext}

  override def outputContext: (AwsEnvironment) => DynamoDBContext = {e => e.createDynamoDBContext}
}





//object AwsNispero {
//  def apply[In, Out, InQueue <:  AnyQueue.of2[In, DynamoDBContext], OutQueue <:  AnyQueue.of2[Out, DynamoDBContext]](
//                                                                                inputQueue: InQueue,
//                                                                                outputQueue: OutQueue,
//                                                                                instructions: Instructions[In, Out],
//                                                                                configuration: AwsNisperoConfiguration):
//  AwsNispero[In, Out, DynamoDBContext, DynamoDBContext, InQueue, OutQueue] = new AwsNispero[In, Out, DynamoDBContext, DynamoDBContext, InQueue, OutQueue](
//  inputQueue, {t: AwsEnvironment => t.createDynamoDBContext},
//  outputQueue,{t: AwsEnvironment => t.createDynamoDBContext},
//  instructions,
//  configuration)
//}


