package ohnosequences.compota

import ohnosequences.compota.queues.{Queue, QueueAux}
import ohnosequences.compota.worker.{Worker, WorkerAux}

trait NisperoAux {
  //type Input
  type InputQueue <: QueueAux

  //type Output
  type OutputQueue <: QueueAux

  type Instr <: InstructionsAux

  val inputQueue: InputQueue
  val outputQueue: OutputQueue
  val instructions: InstructionsAux


  val name: String

  type W <: WorkerAux
  val worker: W
}

//object NisperoAux {
//  type of[In, Out, InQueue <: Queue[In], OutQueue <: Queue[Out]] = NisperoAux {
//     type InputQueue = InQueue
//     type OutputQueue = OutQueue
//     type Instr = Instructions[In, Out]
//     type W = Worker[In, Out, InQueue, OutQueue]
//    //
//  }
//}
class Nispero[In, Out, InQueue <: Queue[In], OutQueue <: Queue[Out]](
                                                                      val name: String,
                                                                      val inputQueue: InQueue,
                                                                      val outputQueue: OutQueue,
                                                                      val instructions: Instructions[In, Out])
  extends NisperoAux {
  type InputQueue = InQueue
  type OutputQueue = OutQueue
  type Instr = Instructions[In, Out]

  type W = Worker[In, Out, InQueue, OutQueue]
  val worker = new Worker[In, Out, InQueue, OutQueue](Nispero.this)


}
