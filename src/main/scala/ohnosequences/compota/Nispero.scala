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

  def instructions: Instructions[NisperoInput, NisperoOutput]

  type NisperoConfiguration <: AnyNisperoConfiguration

  def configuration: NisperoConfiguration

  def worker =  new Worker[NisperoInput, NisperoOutput, NisperoEnvironment, NisperoInputContext, NisperoOutputContext, NisperoInputQueue, NisperoOutputQueue](inputQueue,
    inputContext,
    outputQueue,
    outputContext,
    instructions,
    configuration.name)


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
