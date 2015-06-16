package ohnosequences.compota.metamanager

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, Executors, ExecutorService}

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.{QueueChecker, NisperoGraph}
import ohnosequences.compota.queues._
import ohnosequences.compota.{AnyNispero, TerminationDaemon, AnyCompota, Namespace}


import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

import scala.concurrent._

trait AnyMessageLoopContext extends AnyProcessContext {
  type Command <: AnyMetaManagerCommand
  type QueueContext
  type QueueMessage <: AnyQueueMessage.of[Command]
  val controlQueue: AnyQueue.of1[Command, QueueContext, QueueMessage]
  def controlQueueOp: AnyQueueOp.of2[Command, QueueMessage]
  val reader: AnyQueueReader.of[Command, QueueMessage]
  val writer: AnyQueueWriter.of[Command]
}

object AnyMessageLoopContext {
  type of4[E, Cmd, Ctx, M <: AnyQueueMessage.of[Cmd]] = AnyMessageLoopContext {
    type Environment = E
    type Command = Cmd
    type QueueContext = Ctx
    type QueueMessage = M
  }
}

case class MessageLoopContext[
  E <: AnyEnvironment[E],
  Cmd <: AnyMetaManagerCommand,
  Ctx,
  M <: AnyQueueMessage.of[Cmd]
//  C <: AnyQueue.of1[Cmd, Ctx, M]
 ](
  env: E,
  queueOps: List[AnyQueueOp],
  queueChecker: QueueChecker[E],
  controlQueue: AnyQueue.of1[Cmd, Ctx, M],
  controlQueueOp: AnyQueueOp.of2[Cmd, M],
  reader: AnyQueueReader.of[Cmd, M],
  writer: AnyQueueWriter.of[Cmd]
) extends AnyMessageLoopContext {
  override type Environment = E
  override type Command = Cmd
  override type QueueContext = Ctx
  override type QueueMessage = M

}

trait AnyProcessContext {
  type Environment <: AnyEnvironment[Environment]
  val env: Environment
  def controlQueueOp: AnyQueueOp
  val queueOps: List[AnyQueueOp]
  val queueChecker: QueueChecker[Environment]
}

object AnyMetaManagerContext {
  type of[E <: AnyEnvironment[E]] = AnyProcessContext {
    type Environment = E
  }
}

case class ProcessContext[E <: AnyEnvironment[E]](env: E,
                                                      controlQueueOp: AnyQueueOp,
                                                      queueOps: List[AnyQueueOp],
                                                      queueChecker: QueueChecker[E]
                                                       ) extends AnyProcessContext {
  override type Environment = E
}

//abstract manager without command queue manipulations

trait AnyMetaManager {

  type MetaManagerCommand <: AnyMetaManagerCommand

  type MetaManagerUnDeployingActionContext

  type MetaManagerEnvironment <: AnyEnvironment[MetaManagerEnvironment]

  type MetaManagerControlQueueContext

  type MetaManagerNispero <: AnyNispero.of[MetaManagerEnvironment]

  type MetaManagerCompota <: AnyCompota.of3[MetaManagerEnvironment, MetaManagerUnDeployingActionContext, MetaManagerNispero]

  val controlQueue: AnyQueue.of2[MetaManagerCommand, MetaManagerControlQueueContext]

  val compota: MetaManagerCompota

  def initMessage: MetaManagerCommand

  def controlQueueContext(env: MetaManagerEnvironment): MetaManagerControlQueueContext

  def process(command: MetaManagerCommand, ctx: AnyMetaManagerContext.of[MetaManagerEnvironment]): Try[List[MetaManagerCommand]]

  def printMessage(message: String): String = {
    message.split(System.lineSeparator()).toList match {
      case line1 :: line2 :: tail => {
        if (line1.length > 50) {
          line1.take(50) + "..."
        } else {
          line1
        }      }
      case _ => {
        if (message.length > 50) {
          message.take(50) + "..."
        } else {
          message
        }
      }
    }
  }


  def launchMetaManager(env: MetaManagerEnvironment//,
                       // controlQueueContext: MetaManagerEnvironment => MetaManagerControlQueueContext
                       // launchTerminationDaemon: (QueueChecker[MetaManagerEnvironment], MetaManagerEnvironment) => Try[TerminationDaemon[MetaManagerEnvironment]],
                       // launchConsole: (QueueChecker[MetaManagerEnvironment], AnyQueueOp, MetaManagerEnvironment) => Try[AnyConsole]

  ): Unit = {


    @tailrec
    def messageLoop(ctx: AnyMessageLoopContext.of4[MetaManagerEnvironment, MetaManagerCommand, MetaManagerControlQueueContext, controlQueue.QueueQueueMessage]): Unit = {

      val executor = env.executor

      if (!env.isStopped) {
        val logger = env.logger
        logger.debug("reading message from control queue")
        ctx.reader.waitForMessage(logger, {env.isStopped}).recoverWith { case t => {
            env.reportError(new Error("couldn't receive message from control queue", t), env.namespace / Namespace.controlQueue)
            Failure(t)
          }
        }.flatMap {
          case None => {
            //stopped
            Success(())
          }
          case Some(message) => {
            logger.debug("parsing message " + printMessage(message.id))
            //logger.info(message.getBody.toString)
            message.getBody.recoverWith { case t =>
              env.reportError(new Error("couldn't parse message " + printMessage(message.id) + " from control queue", t), env.namespace / Namespace.controlQueue)
              Failure(t)
            }.flatMap {
              case None => {
                logger.warn("message " + printMessage(message.id) + " is deleted")
                ctx.controlQueueOp.deleteMessage(message).recoverWith { case t =>
                  env.reportError(new Error("couldn't delete message " + printMessage(message.id) + " from control queue", t), env.namespace / message.id)
                  Failure(t)
                }
              }
              case Some(body) =>
                Try{env.executor.execute (  new Runnable {
                  override def toString: String = body.prefix + " executor"
                  override def run(): Unit = {

                    logger.debug("processing message " + printMessage(body.toString))

                    process(body, ctx) match {
                      case Failure(t) => {
                        //command processing failure
                        env.reportError(t, env.namespace / message.id)
                      }
                      case Success(commands) => {
                        logger.debug("writing result: " + printMessage(commands.toString))
                        ctx.writer.writeRaw(commands.map { c => (printMessage(c.prefix) + "_" + System.currentTimeMillis(), c)}).recoverWith { case t =>
                          env.reportError(new Error("couldn't write message to control queue", t), env.namespace / Namespace.controlQueue)
                          Failure(t)
                        }.flatMap { written =>
                          logger.debug("deleting message: " + printMessage(message.id))
                          ctx.controlQueueOp.deleteMessage(message).recoverWith { case t =>
                            env.reportError(new Error("couldn't delete message " + printMessage(message.id) + " from control queue", t), env.namespace / message.id)
                            Failure(t)
                          }
                        }
                      }
                    }
                  }
                })
                }
            }
          }
        }
        messageLoop(ctx)
      } else {
        env.logger.info("environment stopped")
        Success(())
      }
    }




    val logger = env.logger
    logger.info("starting metamanager")

    while (!env.isStopped) {

        QueueChecker(env, compota.nisperoGraph).recoverWith { case t =>
          env.reportError(new Error("failed to create nispero graph", t), env.namespace / Namespace.queueChecker)
          Failure(t)
        }.flatMap { queueChecker =>
          compota.startedTime(env).recoverWith { case t =>
            env.reportError(new Error("failed detect compota starting time", t), env.namespace / "start_time")
            Failure(t)
          }.flatMap { startedTime =>
            logger.debug("creating control queue context")
            val qContext = controlQueueContext(env)
            logger.debug("creating control queue " + controlQueue.name)
            controlQueue.create(qContext).flatMap { queueOp =>
              logger.debug("creating control queue reader")
              queueOp.reader.flatMap { reader =>
                logger.debug("creating control queue writer")
                queueOp.writer.flatMap { writer =>
                  logger.info("writing init message: " + printMessage(initMessage.toString))
                  writer.writeRaw(List((printMessage(initMessage.toString) + "_" + System.currentTimeMillis(), initMessage))).flatMap { res =>
                    logger.debug("starting message loop")
                    val messageLoopContext = MessageLoopContext[MetaManagerEnvironment, MetaManagerCommand, MetaManagerControlQueueContext, controlQueue.QueueQueueMessage](env, queueChecker.queueOps.toList.map{_._2}, queueChecker, controlQueue, queueOp, reader, writer)
                    messageLoop(messageLoopContext)
                    Success(())
                  }
                }
              }
            }
          }
        }.recover { case t =>
          env.reportError(new Error("Couldn't initiate control queue", t), env.namespace / Namespace.controlQueue)
        }
      }

    logger.info("metamanager finished")
  }
}

object AnyMetaManager {
  type of[E <: AnyEnvironment[E]] = AnyMetaManager { type MetaManagerEnvironment = E }
  type of3[E <: AnyEnvironment[E], CCtx, C <: AnyMetaManagerCommand] = AnyMetaManager {
    type MetaManagerEnvironment = E
    type MetaManagerControlQueueContext = CCtx
    type MetaManagerCommand = C
  }
}

