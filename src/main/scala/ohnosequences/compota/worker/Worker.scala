package ohnosequences.compota.worker

import ohnosequences.compota.environment.Environment
import ohnosequences.compota.logging.{ConsoleLogger, Logger, S3Logger}
import ohnosequences.compota.{Nispero, NisperoAux}
import ohnosequences.compota.queues.Queue
import ohnosequences.compota.tasks.Naming

import scala.util.{Try, Success, Failure}

//instructions executor

trait WorkerAux {
  def start(instance: Environment)
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
class Worker[In, Out, InQueue <: Queue[In], OutQueue <: Queue[Out]](nispero: Nispero[In, Out, InQueue, OutQueue]) extends WorkerAux {


  /**
   * This method in the infinite loop: reads messages from input queue,
   * applies instructions to it, writes the results to output queue and delete input message.
   * The different errors should be handled in the different ways.
   * @param env environment
   */
  def start(env: Environment): Unit = {
    val logger = env.logger
    logger.info("worker " + nispero.name + " started on instance " + env.instanceId)

    //all fail fast
    nispero.instructions.prepare(logger).flatMap { context =>
     nispero.inputQueue.getReader.flatMap { queueReader =>
        nispero.outputQueue.getWriter.flatMap { queueWriter =>
          messageLoop(queueReader, queueWriter, env, context)
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


  def messageLoop(queueReader: nispero.inputQueue.QR, queueWriter: nispero.outputQueue.QW, env: Environment, instructionsContext: nispero.instructions.Context): Try[Unit] = {
    val logger  = env.logger

    //def read

    while (!env.isTerminated) {
      queueReader.getMessage.flatMap { message =>
        message.getId.flatMap { id =>
          message.getBody.flatMap { input =>
            logger.info("input: " + input)
            nispero.instructions.solve(logger, instructionsContext, input).flatMap { output =>
              logger.info("result: " + output)
              queueWriter.write(Naming.generateTasks(nispero, id, output)).flatMap { written =>
                nispero.inputQueue.deleteMessage(message)
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
