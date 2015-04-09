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

  def launchMetaManager[QContext](env: MetaManagerEnvironment, queue: Queue[MetaManagerCommand, QContext], context: MetaManagerEnvironment => QContext): Unit = {

    @tailrec
    def messageLoop(queueOp: QueueOp[MetaManagerCommand, queue.Msg, queue.Reader, queue.Writer], reader: queue.Reader, writer: queue.Writer): Try[Unit] = {
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
          }
        }
      } match {
        case Failure(t) => {
          env.reportError("metamanager", t)
          //todo add fatal error here!
          messageLoop(queueOp, reader, writer)
        }
        case Success(()) => {
          messageLoop(queueOp, reader, writer)
        }
      }
    }


    val logger = env.logger
    logger.info("starting metamanager")

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
    } match {
      case Failure(t) => {
        //fatal error
        env.reportError("metamanager_create_queue", t)
      }
      case Success(queueOp) => {
        ()
      }
    }

  }
}

object AnyMetaManager {
  type of[E <: AnyEnvironment] = AnyMetaManager { type MetaManagerEnvironment = E }
}

//class MetaManager {
//
//  def proccess(command: Command): Try[List[Command]] = {
//    command match {
//      case CreateWorkerGroup(nispero) => {
////        val workersGroup = nispero.nisperoConfiguration.workerGroup
////
////        logger.info("nispero " + nispero.nisperoConfiguration.name + ": generating user script")
////        val script = userScript(worker)
////
////        logger.info("nispero " + nispero.nisperoConfiguration.name + ": launching workers group")
////        val workers = workersGroup.autoScalingGroup(
////          name = nispero.nisperoConfiguration.workersGroupName,
////          defaultInstanceSpecs = nispero.nisperoConfiguration.nisperonConfiguration.defaultInstanceSpecs,
////          amiId = nispero.managerDistribution.ami.id,
////          userData = script
////        )
////
////        aws.as.createAutoScalingGroup(workers)
//        Success(List[Command]())
//      }
//    }
//  }
//}
