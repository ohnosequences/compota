package ohnosequences.compota

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues._
import ohnosequences.compota.worker.{Worker, AnyWorker}

import scala.util.Try

trait AnyNispero {

  type NisperoEnvironment <: AnyEnvironment

  type Input
  type Output

  type InContext
  type OutContext

  //type Input
  type InputQueue <: AnyQueue.of[InContext]
  val inputQueue: InputQueue

  //type Output
  type OutputQueue <: AnyQueue.of[OutContext]
  val outputQueue: OutputQueue

  val inContext: NisperoEnvironment => InContext
  val outContext: NisperoEnvironment => OutContext

  val instructions: Instructions[Input, Output]


  def name: String = instructions.name


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
  type of[E <: AnyEnvironment] = AnyNispero { type NisperoEnvironment = E}


}


abstract class Nispero[In, Out, Env <: AnyEnvironment, InCtx, OutCtx, InQueue <: Queue[In, InCtx], OutQueue <: Queue[Out, OutCtx]](
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
  def createWorker() = new Worker[In, Out, Env, InContext, OutContext, InQueue, OutQueue](inputQueue, inContext, outputQueue, outContext, instructions)



}
