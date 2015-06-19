package ohnosequences.compota.metamanager

import ohnosequences.compota.metamanager.UnDeployMetaManger
import ohnosequences.compota.{Namespace, TerminationDaemon}
import ohnosequences.compota.console.AnyConsole
import ohnosequences.compota.graphs.QueueChecker
import ohnosequences.compota.queues.AnyQueueOp

import scala.util.{Failure, Success, Try}


trait BaseMetaManager extends AnyMetaManager {

  override type MetaManagerCommand = BaseMetaManagerCommand

  override def initMessage: BaseMetaManagerCommand = LaunchConsole

  def sendMessageToControlQueue(env: MetaManagerEnvironment, command: BaseMetaManagerCommand): Try[Unit] = {
    Success(()).flatMap { u =>
      controlQueue.create(controlQueueContext(env)).flatMap { controlQueueOp =>
        controlQueueOp.writer.flatMap { writer =>
          writer.writeMessages(printMessage(command.prefix) + System.currentTimeMillis(), List(command))
        }
      }
    }
  }

  override def process(command: BaseMetaManagerCommand,
                       ctx: AnyProcessContext.of[MetaManagerEnvironment, MetaManagerUnDeployingActionContext]
                        ): Try[List[BaseMetaManagerCommand]] = {
    val env = ctx.env
    val queueChecker = ctx.queueChecker
    val controlQueueOp = ctx.controlQueueOp
    val queueOps = ctx.queueOps
    val logger = env.logger
    logger.info("processing tasks " + command)
    command match {

      case LaunchConsole => {

        env.subEnvironmentAsync(Left(Namespace.console)) { env =>
          compota.launchConsole(queueChecker, controlQueueOp, env)
         // SendNotification(compota.configuration.name + " started", message)
        }.map { env =>
          List[BaseMetaManagerCommand](LaunchTerminationDaemon)
        }
      }

      case SendNotification(subject, message) => {

        compota.sendNotification(env, subject, message).map { res =>
          List[BaseMetaManagerCommand]()
        }
      }

      case LaunchTerminationDaemon => {
        env.subEnvironmentAsync(Left(Namespace.terminationDaemon)) { env =>
          compota.launchTerminationDaemon(queueChecker, env)
        }.map { tEnv =>
          List[BaseMetaManagerCommand](AddTasks)
        }
      }

      case AddTasks => {
        compota.compotaDeployed(env).flatMap { deployed =>
          if (deployed) {
            logger.warn("Compota has already been deployed, skipping adding tasks")
            Success(List(CreateNisperoWorkers(0)))
          } else {
            logger.info("adding tasks")
            //todo give user access to writers
//            val queueWriters: Map[String, AnyQueue ctx.queueOps.map { queueOp =>
//              (queueOp.queue, queueOp.writer.get)
//            }.toMap


            compota.addTasks(env).map { res =>
              List(CreateNisperoWorkers(0))
            }
          }
        }
      }


      case CreateNisperoWorkers(index) if index < compota.nisperos.size => {
        val nispero = compota.nisperos(index)
        Try {
          compota.createNisperoWorkers(env, nispero)
        }.flatMap { e => e }.map { res =>
          List(CreateNisperoWorkers(index + 1))
        }
      }

      case CreateNisperoWorkers(index) => {
        Success(List[BaseMetaManagerCommand](PrepareUnDeployActions(false)))
      }

      case PrepareUnDeployActions(executeActions) => {
        ctx.compotaUnDeployActionContext.get match {
          case Some(uCtx) => {
            env.logger.info("undeploy actions are already prepared")
            if (executeActions) {
              Success(List(ExecuteUnDeployActions))
            } else {
              Success(List[BaseMetaManagerCommand]())
            }
          }
          case None => {
            env.logger.info("preparing undeploy actions")
            //started
            compota.prepareUnDeployActions(env).map { uCtx =>
              env.logger.info("saving context")
              ctx.compotaUnDeployActionContext.set(Some(uCtx))
              if (executeActions) {
                List(ExecuteUnDeployActions)
              } else {
                List[BaseMetaManagerCommand]()
              }
            }
          }
        }
      }

      case ForceUnDeploy(reason, message) => {
        compota.forceUnDeploy(env, reason, message).map { r =>
          List[BaseMetaManagerCommand]()
        }
      }

      case UnDeploy => {
        Success(List(DeleteNisperoWorkers(0)))
      }

//      case UnDeploy(reason, force) => {
//        logger.info("undeploying reason: " + reason + " force: " + force)
//        Success(List(DeleteNisperoWorkers(0, reason, force)))
//      }

      case DeleteNisperoWorkers(index) if index < compota.nisperos.size => {
        val nispero = compota.nisperos(index)
        Try {
          compota.deleteNisperoWorkers(env, nispero)
        }.flatMap { e => e }.map { res =>
          List(DeleteNisperoWorkers(index + 1))
        }
      }

      case DeleteNisperoWorkers(index) => {
        Success(List(ReduceQueue(0)))
      }

      case ReduceQueue(index) if index < queueOps.size  => {
        val queueOp = queueOps(index)
        Success(()).flatMap { u =>
          logger.info("reducing queue " + queueOp)
          queueOp.reduce(env)
        }.map { res =>
          List(ReduceQueue(index + 1))
        }
      }

      case ReduceQueue(index) => {
        Success(List(DeleteQueue(0)))
      }

      case DeleteQueue(index) if index < queueOps.size => {
        logger.info("deleting queue " + queueOps(index).queue.name)
        Try {
          queueOps(index).delete()
        }.flatMap { e => e }.map { res =>
          List(DeleteQueue(index + 1))
        }
      }
      case DeleteQueue(index) => {
        Success(List(ExecuteUnDeployActions))
      }

      case ExecuteUnDeployActions => {
        Success(()).flatMap { u =>
          ctx.compotaUnDeployActionContext.get match {
            case None => {
              //Failure(new Error("compotaUnDeployActionContext is not set but ExecuteUnDeployActions started"))
              Success(List(PrepareUnDeployActions(true)))
            }
            case Some(uCtx) => {
              compota.unDeployActions(env, uCtx).map { message =>
                List(FinishCompota("solved", message))
              }
            }
          }
        }
      }

      case FinishCompota(reason, message) => {
        compota.finishUnDeploy(env, reason, message).map { res =>
          List(UnDeployMetaManger(reason, message))
        }
      }

      case UnDeployMetaManger(reason, message) => {
        Success(()).flatMap { u =>
          logger.info("deleting control queue " + controlQueueOp.queue.name)
          Try {
            compota.sendNotification(env, compota.configuration.name + " finished", "reason: " + reason + System.lineSeparator() + message)
            controlQueueOp.delete()
            compota.deleteManager(env)
            env.stop(recursive = true)
            env.terminate()
          }

        }.map { res =>
          List[BaseMetaManagerCommand]()
        }
      }


      case _ => {
        Failure(new Error("unknown command"))
      }

    }

  }

}
