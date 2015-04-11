package ohnosequences.compota.metamanager

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues.{Queue, QueueOp}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

import scala.concurrent._
import ExecutionContext.Implicits.global

//abstract manager without command queue manipulations

trait AnyMetaManager {
  type MetaManagerEnvironment <: AnyEnvironment
  type MetaManagerCommand <: AnyMetaManagerCommand

  def process(command: MetaManagerCommand, env: MetaManagerEnvironment): Try[List[MetaManagerCommand]]

  def launchMetaManager[QContext](
                                   env: MetaManagerEnvironment,
                                   queue: Queue[MetaManagerCommand, QContext],
                                   context: MetaManagerEnvironment => QContext,
                                   prepareUnDeployingActions: => Try[Unit]): Unit = {

    @tailrec
    def messageLoop(queueOp: QueueOp[MetaManagerCommand, queue.Msg, queue.Reader, queue.Writer], reader: queue.Reader, writer: queue.Writer): Try[Unit] = {
      if (!env.isTerminated) {
        val logger = env.logger
        logger.debug("reading message from control queue")
        reader.receiveMessage(logger, env.isTerminated).recoverWith { case t =>
          env.reportError("metamanager_control_queue", new Error("couldn't reacive message from control queue", t))
          Failure(t)
        }.flatMap { message =>
          logger.debug("parsing message")
          message.getBody.recoverWith { case t =>
            env.reportError("metamanager_control_queue", new Error("couldn't parse message " + message.id + " from control queue", t))
            Failure(t)
          }.map { body =>
            future {
              logger.debug("processing message " + body)
              process(body, env)
            }.onComplete {
              case Failure(t) => {
                //future failure
                env.reportError("metamanager_" + body.prefix, t)
              }
              case Success(Failure(t)) => {
                //command processing failure
                env.reportError("metamanager_" + body.prefix, t)
              }
              case Success(Success(commands)) => {
                logger.debug("writing result: " + commands)
                writer.writeRaw(commands.map { c => (c.prefix, c)}).recoverWith { case t =>
                  env.reportError("metamanager_control_queue", new Error("couldn't write message to control queue", t))
                  Failure(t)
                }.flatMap { written =>
                  logger.debug("deleting message: " + message.id)
                  queueOp.deleteMessage(message).recoverWith { case t =>
                    env.reportError("metamanager_control_queue", new Error("couldn't delete message " + message.id + " from control queue", t))
                    Failure(t)
                  }
                }
              }
            }
          }
        }
        messageLoop(queueOp, reader, writer)
      } else {
        Success(())
      }
    }


    val logger = env.logger
    logger.info("starting metamanager")

    env.repeat {
      logger.debug("creating control queue context")
      val qContext = context(env)
      logger.debug("creating control queue " + queue.name)
      queue.create(qContext).flatMap { queueOp =>
        logger.debug("creating control queue reader")
        queueOp.reader.flatMap { reader =>
          logger.debug("creating control queue writer")
          queueOp.writer.flatMap { writer =>
            logger.debug("starting message loop")
            messageLoop(queueOp, reader, writer)
          }
        }
      }.recover { case t =>
        env.reportError("metamanager_control_queue_init", new Error("Couldn't initiate control queue", t))
      }
    }
  }

}

object AnyMetaManager {
  type of[E <: AnyEnvironment] = AnyMetaManager { type MetaManagerEnvironment = E }
}
