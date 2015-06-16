package ohnosequences.compota.metamanager

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
                       ctx: AnyMetaManagerContext.of[MetaManagerEnvironment]
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
        }.map { tEnv =>
          List[BaseMetaManagerCommand](LaunchTerminationDaemon)
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
        Success(List[BaseMetaManagerCommand](PrepareUnDeployActions))
      }

      case PrepareUnDeployActions => {
        compota.compotaUnDeployActionContext.get match {
          case Some(ctx) => {
            env.logger.info("already prepared")
            Success(List[BaseMetaManagerCommand]())
          }
          case None => {
            compota.prepareUnDeployActions(env).map { ctx =>
              env.logger.info("saving context")
              compota.compotaUnDeployActionContext.set(Some(ctx))
              List[BaseMetaManagerCommand]()
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

      case ReduceQueue(index) if index < compota.reducers.size => {
        val reducer = compota.reducers(index)
        Try {
          logger.info("reducing queue " + reducer.queue.name)
          reducer.reduce(env)
        }.flatMap { e => e }.map { res =>
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
          compota.compotaUnDeployActionContext.get match {
            case None => {
              Failure(new Error("compotaUnDeployActionContext is not set but ExecuteUnDeployActions started"))
            }
            case Some(ctx) => {
              compota.unDeployActions(env, ctx).map { message =>
                List(FinishCompota("solved", message))
              }
            }
          }
        }
      }

      case FinishCompota(reason, message) => {
        compota.finishUnDeploy(env, reason, message).map { res =>
          List(UnDeployMetaManger)
        }
      }

      case UnDeployMetaManger => {
        Success(()).flatMap { u =>
          logger.info("deleting control queue " + controlQueueOp.queue.name)
          env.stop()
          controlQueueOp.delete()
          compota.deleteManager(env)
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
