package ohnosequences.compota

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues._
import ohnosequences.compota.worker.{Worker, AnyWorker}

import scala.util.Try

trait AnyNispero {

  type NisperoEnvironment <: AnyEnvironment[NisperoEnvironment]

  type Input
  type Output

  type InContext
  type OutContext

  //type Input
  type InputQueue <: AnyQueue.of2[Input, InContext]
  val inputQueue: InputQueue

  //type Output
  type OutputQueue <: AnyQueue.of2[Output, OutContext]
  val outputQueue: OutputQueue

  val inContext: NisperoEnvironment => InContext
  val outContext: NisperoEnvironment => OutContext

  val instructions: Instructions[Input, Output]

  val configuration: AnyNisperoConfiguration


 // def name: String = instructions.name


  type W <: AnyWorker {type WorkerEnvironment = NisperoEnvironment}

  def createWorker(): W


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
    type Input = I
    type Output = O
    type InputQueue = IQ
    type OutputQueue = OQ
  }


}


abstract class Nispero[In, Out, Env <: AnyEnvironment[Env], InCtx, OutCtx, InQueue <:  AnyQueue.of2[In, InCtx], OutQueue <:  AnyQueue.of2[Out, OutCtx]](
  val inputQueue: InQueue,
  val inContext: Env => InCtx,
  val outputQueue: OutQueue,
  val outContext: Env => OutCtx,
  val instructions: Instructions[In, Out],
  val configuration: AnyNisperoConfiguration
)
extends AnyNispero { nispero =>

  type InContext = InCtx
  type OutContext = OutCtx

  type InputQueue = InQueue
  type OutputQueue = OutQueue

  type NisperoEnvironment = Env

  type Input = In
  type Output = Out

  type W = Worker[In, Out, Env, InContext, OutContext, InQueue, OutQueue]
  def createWorker() = new Worker[In, Out, Env, InContext, OutContext, InQueue, OutQueue](inputQueue,
    inContext,
    outputQueue,
    outContext,
    instructions,
    configuration.name)



}
