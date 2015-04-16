package ohnosequences.compota.metamanager

import java.util.concurrent.{Executors, ExecutorService}

import ohnosequences.compota.environment.AnyEnvironment
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.queues.{AnyQueueOp, Queue, QueueOp}
import ohnosequences.compota.{TerminationDaemon, AnyCompota, Namespace}
import ohnosequences.compota.Namespace._


import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

import scala.concurrent._

//abstract manager without command queue manipulations

trait AnyMetaManager {

  type MetaManagerCommand <: AnyMetaManagerCommand

  type MetaManagerUnDeployingActionContext

  type MetaManagerEnvironment <: AnyEnvironment

  type MetaManagerCompota <: AnyCompota.of[MetaManagerEnvironment, MetaManagerUnDeployingActionContext]

  val compota: MetaManagerCompota

  def initMessage(): MetaManagerCommand


  def process(command: MetaManagerCommand,
              env: MetaManagerEnvironment,
              unDeployActionsContext: MetaManagerUnDeployingActionContext,
              controlQueueOp: AnyQueueOp,
              queues: List[AnyQueueOp],
              terminationDaemon: TerminationDaemon[MetaManagerEnvironment]
               ): Try[List[MetaManagerCommand]]

  def launchMetaManager[QContext](
                                   env: MetaManagerEnvironment,
                                   queue: Queue[MetaManagerCommand, QContext],
                                   context: MetaManagerEnvironment => QContext

  ): Unit = {

    @tailrec
    def messageLoop(queueOp: QueueOp[MetaManagerCommand, queue.Msg, queue.Reader, queue.Writer],
                    reader: queue.Reader,
                    writer: queue.Writer,
                    unDeployingActionsContext: MetaManagerUnDeployingActionContext,
                    queueOps: List[AnyQueueOp],
                    terminationDaemon: TerminationDaemon[MetaManagerEnvironment]): Unit = {

      val executor = Executors.newCachedThreadPool()
      implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(executor)

      if (!env.isStopped) {
        val logger = env.logger
        logger.debug("reading message from control queue")
        reader.waitForMessage(logger, {env.isStopped}).recoverWith { case t => {
            env.reportError(metaManager / controlQueue, new Error("couldn't receive message from control queue", t))
            Failure(t)
          }
        }.flatMap {
          case None => {
            Success(())
          }
          case Some(message) => {
            logger.debug("parsing message " + message.id)
            //logger.info(message.getBody.toString)
            message.getBody.recoverWith { case t =>
              env.reportError(metaManager / controlQueue, new Error("couldn't parse message " + message.id + " from control queue", t))
              Failure(t)
            }.flatMap { body =>
              Try{env.executor.execute (  new Runnable {
                override def toString: String = body.prefix + " executor"
                override def run(): Unit = {
                  logger.debug("processing message " + body)
                  process(body, env, unDeployingActionsContext, queueOp, queueOps, terminationDaemon) match {
                    case Failure(t) => {
                      //command processing failure
                      env.reportError(metaManager / body.prefix, t)
                    }
                    case Success(commands) => {
                      logger.debug("writing result: " + commands)
                      writer.writeRaw(commands.map { c => (c.prefix, c)}).recoverWith { case t =>
                        env.reportError(metaManager / controlQueue, new Error("couldn't write message to control queue", t))
                        Failure(t)
                      }.flatMap { written =>
                        logger.debug("deleting message: " + message.id)
                        queueOp.deleteMessage(message).recoverWith { case t =>
                          env.reportError(metaManager / controlQueue, new Error("couldn't delete message " + message.id + " from control queue", t))
                          Failure(t)
                        }
                      }
                    }
                  }
                }
              })}
            }
          }
        }
        messageLoop(queueOp, reader, writer, unDeployingActionsContext, queueOps, terminationDaemon)
      } else {
        Success(())
      }
    }


    val logger = env.logger
    logger.info("starting metamanager")

    while (!env.isStopped) {
      Try{compota.prepareUnDeployActions(env)}.flatMap{e => e}.recoverWith { case t =>
        env.reportError(metaManager / unDeployActions / "prepare", new Error("prepareUnDeployActions failed", t))
        Failure(t)
      }.flatMap { unDeployActionsContext =>

        NisperoGraph(env, compota.nisperos).recoverWith { case t =>
          env.reportError(metaManager / "nispero_graph", new Error("failed to create nispero graph", t))
          Failure(t)
        }.flatMap { graph =>

          compota.startedTime().recoverWith { case t =>
            env.reportError(metaManager / "init", new Error("failed detect compota starting time", t))
            Failure(t)
          }.flatMap { startedTime =>

            val terminationDaemon = new TerminationDaemon[MetaManagerEnvironment](
              nisperoGraph = graph,
              sendUnDeployCommand = compota.sendUnDeployCommand,
              startedTime = startedTime,
              timeout = compota.baseConfiguration.timeout,
              terminationDaemonIdleTime = compota.baseConfiguration.terminationDaemonIdleTime
            )
            logger.debug("creating control queue context")
            val qContext = context(env)
            logger.debug("creating control queue " + queue.name)
            queue.create(qContext).flatMap { queueOp =>
              logger.debug("creating control queue reader")
              queueOp.reader.flatMap { reader =>
                logger.debug("creating control queue writer")
                queueOp.writer.flatMap { writer =>
                  logger.info("writing init message")
                  writer.writeRaw(List(("init", initMessage()))).flatMap { res =>
                    logger.debug("starting message loop")
                    messageLoop(queueOp, reader, writer, unDeployActionsContext, graph.queueOps, terminationDaemon)
                    Success(())
                  }
                }
              }
            }
          }
        }.recover { case t =>
          env.reportError(metaManager / controlQueue / "init", new Error("Couldn't initiate control queue", t))
        }
      }
    }
    logger.info("metamanager finished")
  }
}

object AnyMetaManager {
  type of[E <: AnyEnvironment] = AnyMetaManager { type MetaManagerEnvironment = E }
}

