package ohnosequences.compota

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues._
import ohnosequences.compota.worker.{Worker, AnyWorker}

import scala.util.Try

trait AnyNispero {

  type NisperoEnvironment <: AnyEnvironment[NisperoEnvironment]

  type NisperoInput
  type NisperoOutput

  type NisperoInputContext
  type NisperoOutputContext

  type NisperoInputQueue <: AnyQueue.of2[NisperoInput, NisperoInputContext]
  def inputQueue: NisperoInputQueue

  type NisperoOutputQueue <: AnyQueue.of2[NisperoOutput, NisperoOutputContext]
  def outputQueue: NisperoOutputQueue

  def inputContext: NisperoEnvironment => NisperoInputContext
  def outputContext: NisperoEnvironment => NisperoOutputContext

  val instructions: Instructions[NisperoInput, NisperoOutput]

  type NisperoConfiguration <: AnyNisperoConfiguration

  val configuration: NisperoConfiguration


 // def name: String = instructions.name


  //type W <: AnyWorker {type WorkerEnvironment = NisperoEnvironment}



  def createWorker() =  new Worker[NisperoInput, NisperoOutput, NisperoEnvironment, NisperoInputContext, NisperoOutputContext, NisperoInputQueue, NisperoOutputQueue](inputQueue,
    inputContext,
    outputQueue,
    outputContext,
    instructions,
    configuration.name)


//  def deleteInputQueue(env: NisperoEnvironment): Try[Unit] = {
//    inputQueue.delete(inContext(env))
//  }
//
//  def deleteOutputQueue(env: NisperoEnvironment): Try[Unit] = {
//    outputQueue.delete(outContext(env))
//  }

//  def reduceOutputQueue(environment: NisperoEnvironment): Try[Unit] = {
//    outputQueue
//  }

}

object AnyNispero {
  type of[E <: AnyEnvironment[E]] = AnyNispero { type NisperoEnvironment = E}

  type of3[E <: AnyEnvironment[E], I, O, C, IQ <: AnyQueue.of2[I, C], OQ <: AnyQueue.of2[O, C]] = AnyNispero {
    type NisperoEnvironment = E
    type NisperoInputContext = C
    type NisperoOutputContext = C
    type NisperoInput = I
    type NisperoOutput = O
    type NisperoInputQueue = IQ
    type NisperoOutputQueue = OQ
  }


}


//abstract class Nispero[In, Out, Env <: AnyEnvironment[Env], InCtx, OutCtx, InQueue <:  AnyQueue.of2[In, InCtx], OutQueue <:  AnyQueue.of2[Out, OutCtx]](
//  val inputQueue: InQueue,
//  val inContext: Env => InCtx,
//  val outputQueue: OutQueue,
//  val outContext: Env => OutCtx,
//  val instructions: Instructions[In, Out],
//  val configuration: AnyNisperoConfiguration
//)
//extends AnyNispero { nispero =>
//
//  type InContext = InCtx
//  type OutContext = OutCtx
//
//  type InputQueue = InQueue
//  type OutputQueue = OutQueue
//
//  type NisperoEnvironment = Env
//
//  type Input = In
//  type Output = Out
//
//  type W = Worker[In, Out, Env, InContext, OutContext, InQueue, OutQueue]
////  def createWorker() = new Worker[In, Out, Env, InContext, OutContext, InQueue, OutQueue](inputQueue,
////    inContext,
////    outputQueue,
////    outContext,
////    instructions,
////    configuration.name)
//
//}
