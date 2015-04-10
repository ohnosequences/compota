package ohnosequences.compota.metamanager

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.queues.{Queue, QueueOp}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

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
      if(!env.isTerminated) {
        val logger = env.logger
        logger.debug("reading message from control queue")
        reader.receiveMessage(logger, env.isTerminated).flatMap { message =>
          logger.debug("parsing message")
          message.getBody.flatMap { body =>
            logger.debug("processing message " + body)
            process(body, env).flatMap { commands =>
              logger.debug("writing result: " + commands)
              writer.writeRaw(commands.map { c => (c.prefix, c)}).flatMap { written =>
                logger.debug("deleting message: " + message.id)
                queueOp.deleteMessage(message)
              }
            } match {
              case Failure(t) => {
                env.reportError("metamanager_" + body.prefix, t)
                Failure(t)
              }
              case Success(s) => Success(s)
            }
          }
        } match {
          case Failure(t) => {
            if(!env.isTerminated) {
              env.reportError("metamanager_receive_message", t)
            }
            messageLoop(queueOp, reader, writer)
          }
          case Success(()) => {
            messageLoop(queueOp, reader, writer)
          }
        }
      } else {
        Success(())
      }
    }


    val logger = env.logger
    logger.info("starting metamanager")

    env.repeat("metamanager_init", 5, Some(10000)) {

      logger.debug("creating control queue context")
      val qContext = context(env)

      logger.debug("creating control queue " + queue.name)
      queue.create(qContext).flatMap { queueOp =>
        logger.debug("creating control queue reader")
        queueOp.reader.flatMap { reader =>
          logger.debug("creating control queue writer")
          queueOp.writer.flatMap { writer =>
            logger.debug("prepare undeploying action")
            prepareUnDeployingActions.flatMap { res =>
              logger.debug("starting message loop")
              messageLoop(queueOp, reader, writer)
            }
          }
        }
      }
    } match {
      case Failure(t) => {
        //fatal error
        if(!env.isTerminated) {
          env.fatalError("metamanager_init", t)
        }
      }
      case Success(()) => {
        ()
      }
    }

  }
}

object AnyMetaManager {
  type of[E <: AnyEnvironment] = AnyMetaManager { type MetaManagerEnvironment = E }
}
