package ohnosequences.compota.worker

import ohnosequences.compota.{Instructions, Nispero}
import ohnosequences.compota.environment.{AnyEnvironment, Environment}
import ohnosequences.compota.queues.{QueueOp, Queue, AnyQueue}
import ohnosequences.logging.{ConsoleLogger, Logger, S3Logger}
import ohnosequences.compota.tasks.Naming

import scala.annotation.tailrec
import scala.util.{Try, Success, Failure}

// TODO: Worker is not needed here. Nispero is more than enough; add start, etc to Nispero as ops
//instructions executor
trait AnyWorker {
  type QueueContext

  type InputQueue <: AnyQueue //{ type Element = In }
  type OutputQueue <: AnyQueue


   def start(instance: Environment[QueueContext]): Unit
}

/**
  * Worker class execute instructions in an environment: EC2 instance, local thread.
  */
class Worker[In, Out, QCtx, IQ <: Queue[In, QCtx], OQ <: Queue[Out, QCtx]](
   val inputQueue: IQ, val outputQueue: OQ, instructions: Instructions[In, Out]) extends AnyWorker {

   type InputQueue = IQ
   type OutputQueue = OQ

  type QueueContext = QCtx

   /**
    * This method in the infinite loop: reads messages from input queue,
    * applies instructions to it, writes the results to output queue and delete input message.
    * The different errors should be handled in the different ways.
    * @param env environment
    */
   def start(env: Environment[QCtx]): Unit = {
     val logger = env.logger
     logger.info("worker for instructions " + instructions.name + " started on instance " + env.instanceId)
     //all fail fast
     inputQueue.create(env.queueCtx).flatMap { inputQueue =>
       outputQueue.create(env.queueCtx).flatMap { outputQueue =>
         instructions.prepare(logger).flatMap { context =>
           inputQueue.reader.flatMap { queueReader =>
             outputQueue.writer.flatMap { queueWriter =>
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

  def messageLoop(inputQueueOp: QueueOp[In, inputQueue.Msg, inputQueue.Reader, inputQueue.Writer],
                  queueReader: inputQueue.Reader,
                  queueWriter: outputQueue.Writer,
                  env: Environment[QCtx],
                  instructionsContext: instructions.Context): Try[Unit] = {

    val logger = env.logger

    logger.info("start message loop")

    @tailrec
    def messageLoopRec(): Try[Unit] = {
      if (env.isTerminated) {
        Success(())
      } else {
        queueReader.receiveMessage.flatMap { message =>

            message.getBody.flatMap { input =>
              logger.info("input: " + input)
              instructions.solve(logger, instructionsContext, input).flatMap { output =>
                logger.info("result: " + output)
                queueWriter.writeMessages(message.id + "." + instructions.name, output).flatMap { written =>
                  inputQueueOp.deleteMessage(message)
                }
              }
            } match {
              case Failure(t) => {
                //non fatal task error: instructions error, read error, write error, delete error
                env.reportError(message.id, t)
                Success(())
              }
              case success => success
            }
          }
        } match {
          case Success(_) => {
            messageLoopRec()
          }
          case Failure(t) => {
            //fatal error
            logger.error("error during reading from input queue")
            logger.error(t)
            Failure(t)
          }
        }

    }

    messageLoopRec()
  }
}
