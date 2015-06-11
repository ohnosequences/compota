package ohnosequences.compota.metamanager

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, Executors, ExecutorService}

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.{QueueChecker, NisperoGraph}
import ohnosequences.compota.queues.{AnyQueue, AnyQueueOp, Queue, QueueOp}
import ohnosequences.compota.{AnyNispero, TerminationDaemon, AnyCompota, Namespace}


import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

import scala.concurrent._

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

  val runingTasks = new ConcurrentHashMap[String, AtomicInteger]()

  def getProcessingTasks(): List[(String, Int)] = {
    import scala.collection.JavaConversions._
    runingTasks.filter { case (p, i) =>
      i.get() > 0
    }.map { case (p, i) =>
      (p, i.get())
    }.toList
  }



  def process(command: MetaManagerCommand,
              env: MetaManagerEnvironment,
              controlQueueOp: AnyQueueOp,
              queueOps: List[AnyQueueOp],
              queueChecker: QueueChecker[MetaManagerEnvironment]
               ): Try[List[MetaManagerCommand]]

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
    def messageLoop(queueOp: AnyQueueOp.of[MetaManagerCommand, controlQueue.QueueQueueMessage, controlQueue.QueueQueueReader, controlQueue.QueueQueueWriter],
                    reader: controlQueue.QueueQueueReader,
                    writer: controlQueue.QueueQueueWriter,
                    queueOps: List[AnyQueueOp],
                    queueChecker: QueueChecker[MetaManagerEnvironment]): Unit = {

      val executor = env.executor
      //implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(executor)

      if (!env.isStopped) {
        val logger = env.logger
        logger.debug("reading message from control queue")
        reader.waitForMessage(logger, {env.isStopped}).recoverWith { case t => {
            env.reportError(Namespace.metaManager / Namespace.controlQueue, new Error("couldn't receive message from control queue", t))
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
              env.reportError(Namespace.metaManager / Namespace.controlQueue, new Error("couldn't parse message " + printMessage(message.id) + " from control queue", t))
              Failure(t)
            }.flatMap {
              case None => {
                logger.warn("message " + printMessage(message.id) + " is deleted")
                queueOp.deleteMessage(message).recoverWith { case t =>
                  env.reportError(Namespace.metaManager / Namespace.controlQueue, new Error("couldn't delete message " + printMessage(message.id) + " from control queue", t))
                  Failure(t)
                }
              }
              case Some(body) =>
                Try{env.executor.execute (  new Runnable {
                  override def toString: String = body.prefix + " executor"
                  override def run(): Unit = {
                    runingTasks.putIfAbsent(body.prefix, new AtomicInteger())
                    runingTasks.get(body.prefix).incrementAndGet()

                    logger.debug("processing message " + printMessage(body.toString))
                    process(body, env, queueOp, queueOps, queueChecker) match {
                      case Failure(t) => {
                        //command processing failure
                        env.reportError(Namespace.metaManager / body.prefix, t)
                      }
                      case Success(commands) => {
                        logger.debug("writing result: " + printMessage(commands.toString))
                        writer.writeRaw(commands.map { c => (printMessage(c.prefix) + "_" + System.currentTimeMillis(), c)}).recoverWith { case t =>
                          env.reportError(Namespace.metaManager / Namespace.controlQueue, new Error("couldn't write message to control queue", t))
                          Failure(t)
                        }.flatMap { written =>
                          logger.debug("deleting message: " + printMessage(message.id))
                          queueOp.deleteMessage(message).recoverWith { case t =>
                            env.reportError(Namespace.metaManager / Namespace.controlQueue, new Error("couldn't delete message " + printMessage(message.id) + " from control queue", t))
                            Failure(t)
                          }
                        }
                      }
                    }
                    runingTasks.get(body.prefix).decrementAndGet()
                  }
                })
                }
            }
          }
        }
        messageLoop(queueOp, reader, writer, queueOps, queueChecker)
      } else {
        env.logger.info("environment stopped")
        Success(())
      }
    }




    val logger = env.logger
    logger.info("starting metamanager")

    while (!env.isStopped) {

        QueueChecker(env, compota.nisperoGraph).recoverWith { case t =>
          env.reportError(Namespace.metaManager / "nispero_graph", new Error("failed to create nispero graph", t))
          Failure(t)
        }.flatMap { queueChecker =>

          compota.startedTime(env).recoverWith { case t =>
            env.reportError(Namespace.metaManager / "init", new Error("failed detect compota starting time", t))
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
                    messageLoop(
                      queueOp,
                      reader,
                      writer,
                      queueChecker.queueOps.toList.map{_._2},
                      queueChecker
                    )
                    Success(())
                  }
                }
              }
            }
          }
        }.recover { case t =>
          env.reportError(Namespace.metaManager / Namespace.controlQueue / "init", new Error("Couldn't initiate control queue", t))
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

