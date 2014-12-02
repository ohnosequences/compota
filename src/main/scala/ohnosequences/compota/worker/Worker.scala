package ohnosequences.compota.worker

import ohnosequences.compota.Nispero
import ohnosequences.compota.environment.{AnyEnvironment, Environment}
import ohnosequences.compota.queues.{QueueOps, Queue, AnyQueue}
import ohnosequences.logging.{ConsoleLogger, Logger, S3Logger}
import ohnosequences.compota.tasks.Naming

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
   val nispero: Nispero[In, Out, QCtx, IQ, OQ]) extends AnyWorker {

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
     logger.info("worker " + nispero.name + " started on instance " + env.instanceId)
     //all fail fast
     nispero.inputQueue.create(env.queueCtx).flatMap { inputQueue =>
       nispero.outputQueue.create(env.queueCtx).flatMap { outputQueue =>
         nispero.instructions.prepare(logger).flatMap { context =>
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


   def messageLoop(inputQueueOp: QueueOps[In, nispero.inputQueue.Msg, nispero.inputQueue.Reader, nispero.inputQueue.Writer],
                   queueReader: nispero.inputQueue.Reader,
                   queueWriter: nispero.outputQueue.Writer,
                   env: Environment[QCtx],
                   instructionsContext: nispero.instructions.Context): Try[Unit] = {
     val logger  = env.logger

     //def read

     while (!env.isTerminated) {
       queueReader.receiveMessage.flatMap { message =>
         message.getId.flatMap { id =>
           message.getBody.flatMap { input =>
             logger.info("input: " + input)
             nispero.instructions.solve(logger, instructionsContext, input).flatMap { output =>
               logger.info("result: " + output)
               queueWriter.writeMessages(id + "." + nispero, output).flatMap { written =>
                 inputQueueOp.deleteMessage(message)
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
