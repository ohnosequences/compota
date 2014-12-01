package ohnosequences.compota.worker

import ohnosequences.compota.environment.Environment
import ohnosequences.logging.{ConsoleLogger, Logger, S3Logger}
import ohnosequences.compota.{NisperoAux, Nispero}
import ohnosequences.compota.queues.{QueueOp, Queue}
import ohnosequences.compota.tasks.Naming

import scala.util.{Try, Success, Failure}

//instructions executor

trait WorkerAux {
  type QueueContext
  def start(instance: Environment[QueueContext])
}

/**
 * Worker class execute instructions in an environment: EC2 instance, local thread.
 *
 * @param nispero nispero of the worker
 * @tparam In input type of instructions
 * @tparam Out output type of instructions
 * @tparam InQueue type of input queue
 * @tparam OutQueue type of output queue
 */
class Worker[In, Out, QCtx, InQueue <: Queue[In, QCtx], OutQueue <: Queue[Out, QCtx]](nispero: Nispero[In, Out, QCtx, InQueue, OutQueue]) extends WorkerAux {


  /**
   * This method in the infinite loop: reads messages from input queue,
   * applies instructions to it, writes the results to output queue and delete input message.
   * The different errors should be handled in the different ways.
   * @param env environment
   */

  type QueueContext = QCtx
  def start(env: Environment[QCtx]): Unit = {
    val logger = env.logger
    logger.info("worker " + nispero.name + " started on instance " + env.instanceId)

    //all fail fast
    nispero.inputQueue.create(env.queueCtx).flatMap { inputQueue =>
      nispero.outputQueue.create(env.queueCtx).flatMap { outputQueue =>
        nispero.instructions.prepare(logger).flatMap { context =>
          inputQueue.getReader.flatMap { queueReader =>
            outputQueue.getWriter.flatMap { queueWriter =>
              messageLoop(inputQueue, queueReader, queueWriter, env, context)
            }
          }
        }
      }
    } match {
      case Success(_) => {
        //terminated
      }
      case Failure(t) => {
        //fatal error
        env.fatalError(t)
      }
    }
  }


  def messageLoop(queueOp: QueueOp[nispero.inputQueue.Element, nispero.inputQueue.Message, nispero.inputQueue.QR, nispero.inputQueue.QW], queueReader: nispero.inputQueue.QR, queueWriter: nispero.outputQueue.QW, env: Environment[QCtx], instructionsContext: nispero.instructions.Context): Try[Unit] = {
    val logger  = env.logger

    //def read

    while (!env.isTerminated) {
      queueReader.receiveMessage.flatMap { message =>
        message.getId.flatMap { id =>
          message.getBody.flatMap { input =>
            logger.info("input: " + input)
            nispero.instructions.solve(logger, instructionsContext, input).flatMap { output =>
              logger.info("result: " + output)
              queueWriter.write(id + "." + nispero, output).flatMap { written =>
                queueOp.deleteMessage(message)
               // nispero.inputQueue.deleteMessage(message)
              }
            }
          } match {
            case Failure(t) => {
              //non fatal task error: instructions error, read error, write error, delete error
              env.reportError(id, t)
              Success(())
            }
            case success => success
          }

        }
      } match {
        case Success(_) => {}
        case Failure(t) => {
          logger.error("error during reading from input queue")
          logger.error(t)
        }
      }
    }
    Success(())
  }
}
