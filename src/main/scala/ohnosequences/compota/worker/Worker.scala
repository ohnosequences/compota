package ohnosequences.compota.worker

import ohnosequences.compota.{Instructions}
import ohnosequences.compota.environment.{AnyEnvironment}
import ohnosequences.compota.queues.{QueueOp, Queue, AnyQueue}
import org.apache.commons.io.FileUtils

import scala.annotation.tailrec
import scala.util.{Try, Success, Failure}

//instructions executor
trait AnyWorker {
  type WorkerEnvironment

  type InputQueue <: AnyQueue
  type OutputQueue <: AnyQueue

  def start(instance: WorkerEnvironment): Unit
}

/**
  * Worker class execute instructions in an environment: EC2 instance, local thread.
  */
class Worker[In, Out, Env <: AnyEnvironment, InContext, OutContext, IQ <: Queue[In, InContext], OQ <: Queue[Out, OutContext]](
   val inputQueue: IQ, val inContext: Env => InContext,
   val outputQueue: OQ, val outContext: Env => OutContext,
   instructions: Instructions[In, Out]
) extends AnyWorker {

   type InputQueue = IQ
   type OutputQueue = OQ

   type WorkerEnvironment = Env

   /**
    * This method in the infinite loop: reads messages from input queue,
    * applies instructions to it, writes the results to output queue and delete input message.
    * The different errors should be handled in the different ways.
    * @param env environment
    */
   def start(env: Env): Unit = {
     val logger = env.logger
     logger.info("worker for instructions " + instructions.name + " started on instance " + env.instanceId)
     //all fail fast
     env.repeat("preparing worker", 5, timeout = Some(10000)) {
       logger.debug("create context for queue " + inputQueue.name)
       inputQueue.create(inContext(env)).flatMap { inputQueue =>
         logger.debug("create context for queue " + outputQueue.name)
         outputQueue.create(outContext(env)).flatMap { outputQueue =>
           logger.debug("preparing instructions")
           instructions.prepare(logger).flatMap { context =>
             logger.debug("creating reader for queue " + inputQueue.queue.name)
             inputQueue.reader.flatMap { queueReader =>
               logger.debug("creating writer for queue " + outputQueue.queue.name)
               outputQueue.writer.flatMap { queueWriter =>
                 messageLoop(inputQueue, queueReader, queueWriter, env, context)
               }
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
         if (!env.isTerminated) {
           env.fatalError("worker_" + instructions.name + "_init", t)
         }
       }
     }
   }

  def messageLoop(inputQueueOp: QueueOp[In, inputQueue.Msg, inputQueue.Reader, inputQueue.Writer],
                  queueReader: inputQueue.Reader,
                  queueWriter: outputQueue.Writer,
                  env: Env,
                  instructionsContext: instructions.Context): Try[Unit] = {

    val logger = env.logger

    logger.info("start message loop")

    @tailrec
    def messageLoopRec(): Try[Unit] = {
      if (env.isTerminated) {
        Success(())
      } else {
        logger.debug("receiving message from queue " + inputQueue.name)
        queueReader.receiveMessage(logger, env.isTerminated).flatMap { message =>

          logger.debug("parsing the message " + message.id)
          message.getBody.flatMap { input =>
            logger.info("received: " + input.toString.take(100) + " id: " + message.id)

            logger.debug("cleaning working directory: " + env.workingDirectory)
            FileUtils.cleanDirectory(env.workingDirectory)

            logger.debug("running " + instructions.name + " instructions")
            instructions.solve(logger, instructionsContext, input).flatMap { output =>
              logger.info("result: " + output.toString.take(100))

              logger.debug("writing result to queue " + outputQueue.name + " with message id " + message.id + "." + instructions.name)
              queueWriter.writeMessages(message.id + "." + instructions.name, output).flatMap { written =>
                logger.debug("deleting message with id " + message.id + " from queue " + inputQueue.name)
                inputQueueOp.deleteMessage(message)
              }
            }
          } match {
            case Failure(t) => {
              if (!env.isTerminated) {
                env.reportError(message.id, t)
              }
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
          //receive message error
          if (env.isTerminated) {
            Success(())
          } else {
            Failure(t)
          }
        }
      }
    }

    messageLoopRec()
  }
}
