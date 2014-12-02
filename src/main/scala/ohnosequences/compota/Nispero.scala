package ohnosequences.compota

import ohnosequences.compota.queues._
import ohnosequences.compota.worker.{Worker, AnyWorker}
import scala.util.{Try, Success, Failure}

trait AnyNispero {

  type Input
  type Output
  //type Input
  type InputQueue <: AnyQueue
  val inputQueue: InputQueue

  //type Output
  type OutputQueue <: AnyQueue
  val outputQueue: OutputQueue

  val instructions: Instructions[Input, Output]


  val name: String

  // TODO: What's this??
  type QueueCtx


  type W <: AnyWorker {type QueueContext = QueueCtx}
  def createWorker(): W
  // this does not make sense: a worker needs a nispero, a nispero needs a worker, lalala
  // type Worker <: AnyWorker // {type QueueContext = QCtxCtx}
  // val worker: W
}

abstract class Nispero[In, Out, QCtx, InQueue <: Queue[In, QCtx], OutQueue <: Queue[Out, QCtx]](
  val name: String,
  val inputQueue: InQueue,
  val outputQueue: OutQueue,
  val instructions: Instructions[In, Out]
  // why not an env val here?
)
extends AnyNispero { nispero =>

  type InputQueue = InQueue
  type OutputQueue = OutQueue

  type QueueCtx = QCtx


  type Input = In
  type Output = Out

  type W = Worker[In, Out, QCtx, InQueue, OutQueue]
  def createWorker() = new Worker[In, Out, QCtx, InQueue, OutQueue](Nispero.this)

}
