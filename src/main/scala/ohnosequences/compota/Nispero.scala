package ohnosequences.compota

import ohnosequences.compota.queues._
import ohnosequences.compota.environment._
import scala.util.{Try, Success, Failure}

trait AnyNispero {
  //type Input
  type InputQueue <: AnyQueue

  //type Output
  type OutputQueue <: AnyQueue

  type Instructions <: AnyInstructions

  val inputQueue: InputQueue
  val outputQueue: OutputQueue
  val instructions: Instructions

  val name: String

  // TODO: What's this??
  // type QCtxCtx

  type Environment <: AnyEnvironment

  def inputQueueOps(env: Environment): Try[QueueOps[InputQueue]]
  def outputQueueOps(env: Environment): Try[QueueOps[OutputQueue]]
  // TODO: Unit is not a good type here (and almost everywhere)
  def start(env: Environment): Unit
  def messageLoop(env: Environment): Try[Unit] 


  // this does not make sense: a worker needs a nispero, a nispero needs a worker, lalala
  // type Worker <: AnyWorker // {type QueueContext = QCtxCtx}
  // val worker: W
}

abstract class Nispero[
  IQ <: AnyQueue, 
  OQ <:AnyQueue, 
  Instr <: AnyInstructions { type InputMessage = IQ#Message; type OutputMessage = OQ#Message},
  Env <: AnyEnvironment
](
  val name: String,
  val inputQueue: IQ,
  val outputQueue: OQ,
  val instructions: Instr
  // why not an env val here?
)
extends AnyNispero { nispero =>

  type InputQueue = IQ
  type OutputQueue = OQ
  type Instructions = Instr

  type Environment = Env

  def start(env: Environment): Unit = {
    
    // val logger = env.logger
    // logger.info("worker " + nispero.name + " started on instance " + env.instanceId)

    // // TODO: reimplement this
    // //all fail fast
    // nispero.inputQueueOps(env).flatMap { inputQueue =>
    //   nispero.outputQueueOps(env).flatMap { outputQueue =>
    //     nispero.instructions.prepare(logger).flatMap { context =>
    //       inputQueue.getReader.flatMap { queueReader =>
    //         outputQueue.getWriter.flatMap { queueWriter =>
    //           messageLoop(inputQueue, queueReader, queueWriter, env, context)
    //         }
    //       }
    //     }
    //   }
    // } match {
    //   case Success(_) => {
    //     //terminated
    //   }
    //   case Failure(t) => {
    //     //fatal error
    //     env.fatalError(t)
    //   }
    // }
  }

  def messageLoop(env: Environment): Try[Unit] = ??? //{

    // TODO reimplement this

  //   val logger  = env.logger

  //   //def read

  //   while (!env.isTerminated) {
  //     queueReader.receiveMessage.flatMap { message =>
  //       message.getId.flatMap { id =>
  //         message.getBody.flatMap { input =>
  //           logger.info("input: " + input)
  //           nispero.instructions.solve(logger, instructionsContext, input).flatMap { output =>
  //             logger.info("result: " + output)
  //             queueWriter.write(id + "." + nispero, output).flatMap { written =>
  //               queueOp.deleteMessage(message)
  //              // nispero.inputQueue.deleteMessage(message)
  //             }
  //           }
  //         } match {
  //           case Failure(t) => {
  //             //non fatal task error: instructions error, read error, write error, delete error
  //             env.reportError(id, t)
  //             Success(())
  //           }
  //           case success => success
  //         }

  //       }
  //     } match {
  //       case Success(_) => {}
  //       case Failure(t) => {
  //         logger.error("error during reading from input queue")
  //         logger.error(t)
  //       }
  //     }
  //   }
  //   Success(())
  // }
}
