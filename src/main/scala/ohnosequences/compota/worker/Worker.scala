package ohnosequences.compota.worker

import ohnosequences.compota.Instructions
import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues.{AnyQueue, AnyQueueOp}
import org.apache.commons.io.FileUtils

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

//instructions executor
trait AnyWorker {
  type WorkerEnvironment <: AnyEnvironment[WorkerEnvironment]

  type InputQueue <: AnyQueue
  type OutputQueue <: AnyQueue

  def nisperoName: String

  def start(instance: WorkerEnvironment): Unit
}

/**
 * Worker class execute instructions in an environment: EC2 instance, local thread.
 */
class Worker[In, Out, Env <: AnyEnvironment[Env], InContext, OutContext, IQ <: AnyQueue.of2[In, InContext], OQ <: AnyQueue.of2[Out, OutContext]](
                                                                                                                                                  val inputQueue: IQ, val inContext: Env => InContext,
                                                                                                                                                  val outputQueue: OQ, val outContext: Env => OutContext,
                                                                                                                                                  instructions: Instructions[In, Out],
                                                                                                                                                  val nisperoName: String
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
    logger.info("worker of mispero " + nisperoName + " started on instance " + env.instanceId)
    //all fail fast
    while (!env.isStopped) {
      logger.debug("create context for queue " + inputQueue.name)
      inputQueue.create(inContext(env)).flatMap { inputQueue =>
        logger.debug("create context for queue " + outputQueue.name)
        outputQueue.create(outContext(env)).flatMap { outputQueue =>
          logger.debug("preparing instructions")
          instructions.prepare(env).flatMap { context =>
            logger.debug("creating reader for queue " + inputQueue.queue.name)
            inputQueue.reader.flatMap { queueReader =>
              logger.debug("creating writer for queue " + outputQueue.queue.name)
              outputQueue.writer.flatMap { queueWriter =>
                messageLoop(inputQueue, queueReader, queueWriter, env, context)
                Success(())
              }
            }
          }
        }
      }.recover { case t =>
        env.reportError(t, env.namespace / "init")
      }
    }

    logger.info("worker finished")

  }


  def messageLoop(inputQueueOp: AnyQueueOp.of[In, inputQueue.QueueQueueMessage, inputQueue.QueueQueueReader, inputQueue.QueueQueueWriter],
                  queueReader: inputQueue.QueueQueueReader,
                  queueWriter: outputQueue.QueueQueueWriter,
                  env: Env,
                  instructionsContext: instructions.Context): Unit = {

    val logger = env.logger


    @tailrec
    def messageLoopRec(): Unit = {
      if (env.isStopped) {
        Success(())
      } else {
        logger.debug("receiving message from queue " + inputQueue.name)
        queueReader.waitForMessage(logger, {
          env.isStopped
        }).recoverWith { case t =>
          env.reportError(new Error("couldn't receive message from the queue " + inputQueue.name, t), env.namespace / "receive_message")
          Failure(t)
        }.foreach {
          case None => ()
          case Some(message) =>
            logger.debug("parsing the message " + message.id)
            message.getBody.recoverWith { case t =>
              env.reportError(new Error("couldn't parse the message " + message.id + " from the queue " + inputQueue.name, t), env.namespace / message.id)
              Failure(t)
            }.flatMap {
              case None => {
                logger.warn("message " + message.id + " deleted")
                inputQueueOp.deleteMessage(message).recoverWith { case t =>
                  env.reportError(new Error("couldn't delete message " + message.id + " from the queue " + inputQueue.name, t), env.namespace / message.id)
                  Failure(t)
                }
              }
              case Some(input) =>
                logger.info("received: " + input.toString.take(100) + " id: " + message.id)

                env.subEnvironmentSync(message.id) { env =>
                  val logger = env.logger

                  logger.debug("running " + nisperoName + " instructions")
                  Try {
                    instructions.solve(env, instructionsContext, input)
                  }.flatMap { e => e }.recoverWith { case t =>
                    env.reportError(new Error("instructions error", t), env.namespace / message.id)
                    Failure(t)
                  }.flatMap { output =>
                    logger.info("result: " + output.toString().take(100))
                    val newId = message.id + "." + nisperoName
                    logger.debug("writing result to queue " + outputQueue.name + " with message id " + newId)

                    queueWriter.writeMessages(newId, output).recoverWith { case t =>
                      env.reportError(new Error("couldn't write message " + newId + " to the queue " + outputQueue.name, t), env.namespace / newId)
                      Failure(t)
                    }.flatMap { written =>
                      logger.debug("deleting message with id " + message.id + " from queue " + inputQueue.name)
                      inputQueueOp.deleteMessage(message).recoverWith { case t =>
                        env.reportError(new Error("couldn't delete message " + message.id + " from the queue " + inputQueue.name, t), env.namespace / message.id)
                        Failure(t)
                      }
                      logger.debug("cleaning working directory: " + env.workingDirectory)
                      FileUtils.cleanDirectory(env.workingDirectory)
                      Success(())
                    }
                  }
                }
            }
        }
        messageLoopRec()
      }
    }

    logger.info("start message loop")
    messageLoopRec()
  }
}
