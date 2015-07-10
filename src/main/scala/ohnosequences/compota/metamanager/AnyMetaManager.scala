package ohnosequences.compota.metamanager

import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}
import java.util.concurrent.{ConcurrentHashMap, Executors, ExecutorService}

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.{QueueChecker, NisperoGraph}
import ohnosequences.compota.queues._
import ohnosequences.compota.{AnyNispero, TerminationDaemon, AnyCompota, Namespace}


import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

import scala.concurrent._

trait AnyMessageLoopContext {
  type Environment <: AnyEnvironment[Environment]

  def env: Environment

  type Command <: AnyMetaManagerCommand
  type QueueContext
  type QueueMessage <: AnyQueueMessage.of[Command]

  def controlQueue: AnyQueue.of3[Command, QueueContext, QueueMessage]

  def controlQueueOp: AnyQueueOp.of2[Command, QueueMessage]

  def reader: AnyQueueReader.of[Command, QueueMessage]

  def writer: AnyQueueWriter.of[Command]

  def queueOps: List[AnyQueueOp]

  def queueChecker: QueueChecker[Environment]

  val processingCommands: ConcurrentHashMap[Command, Environment] = new ConcurrentHashMap[Command, Environment]()

  def processContext[U] = ProcessContext[Environment, U](env, controlQueueOp, queueOps, queueChecker)
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
   controlQueue: AnyQueue.of3[Cmd, Ctx, M],
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
  type UnDeployActionsContext

  def env: Environment

  def controlQueueOp: AnyQueueOp

  def queueOps: List[AnyQueueOp]

  def queueChecker: QueueChecker[Environment]

  val compotaUnDeployActionContext: AtomicReference[Option[UnDeployActionsContext]] = new AtomicReference(None)
}

object AnyProcessContext {
  type of[E <: AnyEnvironment[E], U] = AnyProcessContext {
    type Environment = E
    type UnDeployActionsContext = U
  }
}

case class ProcessContext[E <: AnyEnvironment[E], U](env: E,
                                                     controlQueueOp: AnyQueueOp,
                                                     queueOps: List[AnyQueueOp],
                                                     queueChecker: QueueChecker[E]
                                                      ) extends AnyProcessContext {
  override type Environment = E
  override type UnDeployActionsContext = U
}

trait AnyMetaManager {

  type MetaManagerCommand <: AnyMetaManagerCommand

  type MetaManagerUnDeployingActionContext

  type MetaManagerEnvironment <: AnyEnvironment[MetaManagerEnvironment]

  type MetaManagerControlQueueContext

  type MetaManagerNispero <: AnyNispero.of[MetaManagerEnvironment]

  type MetaManagerCompota <: AnyCompota.of3[MetaManagerEnvironment, MetaManagerUnDeployingActionContext, MetaManagerNispero]

  val controlQueue: AnyQueue.of2[MetaManagerCommand, MetaManagerControlQueueContext]

  val compota: MetaManagerCompota

  def initCommand: MetaManagerCommand

  def controlQueueContext(env: MetaManagerEnvironment): MetaManagerControlQueueContext

  def process(command: MetaManagerCommand, ctx: AnyProcessContext.of[MetaManagerEnvironment, MetaManagerUnDeployingActionContext]): Try[List[MetaManagerCommand]]

  def launchMetaManager(env: MetaManagerEnvironment): Unit = {

    @tailrec
    def messageLoop(ctx: AnyMessageLoopContext.of4[MetaManagerEnvironment, MetaManagerCommand, MetaManagerControlQueueContext, controlQueue.QueueQueueMessage],
                    processContext: AnyProcessContext.of[MetaManagerEnvironment, MetaManagerUnDeployingActionContext]): Unit = {

      if (!env.isStopped) {
        val logger = env.logger
        logger.debug("reading message from control queue: " + controlQueue.name)
        ctx.reader.waitForMessage(env).recoverWith {
          case t => {
            env.reportError(new Error("couldn't receive message from control queue", t), env.namespace / Namespace.controlQueue)
            Failure(t)
          }
        }.flatMap {
          case None => {
            //stopped
            Success(())
          }
          case Some(message) => {
            logger.debug("parsing message " + message.id)
            message.getBody.recoverWith { case t =>
              env.reportError(new Error("couldn't parse message " + message.id + " from control queue", t), env.namespace / Namespace.controlQueue)
              Failure(t)
            }.flatMap {
              case None => {
                logger.warn("message " + message.id + " is deleted")
                ctx.controlQueueOp.deleteMessage(message).recoverWith { case t =>
                  env.reportError(new Error("couldn't delete message " + message.id + " from control queue", t), env.namespace / message.id)
                  Failure(t)
                }
              }
              case Some(body) if ctx.processingCommands.containsKey(body) => {
                logger.warn("command " + body.id + " is processing")
                Success(())
              }
              case Some(body) => {
                env.subEnvironmentSync(Left(body.id)) { env =>
                  logger.debug("processing command " + body.id)
                  ctx.processingCommands.put(body, env)
                  process(body, processContext) match {
                    case Failure(t) => {
                      ctx.processingCommands.remove(body)
                      //command processing failure
                      env.reportError(t, env.namespace / message.id)
                      Failure(t)
                    }
                    case Success(commands) => {
                      logger.info("command " + body.id + " processed")
                      ctx.processingCommands.remove(body)
                      logger.debug("results commands: " + commands.map(_.id))
                      val commandsWithIds = commands.map { command =>
                        (command.id, command)
                      }
                      ctx.writer.writeRaw(commandsWithIds).recoverWith { case t =>
                        env.reportError(new Error("couldn't write message to control queue", t), env.namespace / Namespace.controlQueue)
                        Failure(t)
                      }.flatMap { written =>
                        logger.debug("deleting message: " + message.id)
                        ctx.controlQueueOp.deleteMessage(message).recoverWith { case t =>
                          env.reportError(new Error("couldn't delete message " + message.id + " from control queue", t), env.namespace / message.id)
                          Failure(t)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
        messageLoop(ctx, processContext)
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
                logger.info("writing init command: " + initCommand.id)

                writer.writeRaw(List((initCommand.id, initCommand))).flatMap { res =>
                  logger.debug("starting message loop")
                  val messageLoopContext = MessageLoopContext[MetaManagerEnvironment, MetaManagerCommand, MetaManagerControlQueueContext, controlQueue.QueueQueueMessage](
                    env,
                    queueChecker.queueOps.toList.map {
                      _._2
                    },
                    queueChecker,
                    controlQueue, queueOp, reader, writer)
                  logger.debug("starting message queus with context: " + messageLoopContext)
                  messageLoop(messageLoopContext, messageLoopContext.processContext[MetaManagerUnDeployingActionContext])
                  Success(())
                }
              }
            }
          }.recoverWith { case t =>
            env.reportError(new Error("Couldn't initiate control queue", t), env.namespace / Namespace.controlQueue)
            Failure(t)
          }

        }
      }
    }

    logger.info("metamanager finished")
  }
}


object AnyMetaManager {
  type of[E <: AnyEnvironment[E]] = AnyMetaManager {type MetaManagerEnvironment = E}
  type of3[E <: AnyEnvironment[E], CCtx, C <: AnyMetaManagerCommand] = AnyMetaManager {
    type MetaManagerEnvironment = E
    type MetaManagerControlQueueContext = CCtx
    type MetaManagerCommand = C
  }
}

