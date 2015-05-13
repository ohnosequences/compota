package ohnosequences.compota.metamanager

import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.{TerminationDaemon, Namespace}
import ohnosequences.compota.queues.AnyQueueOp

import scala.util.{Failure, Success, Try}


trait BaseMetaManager extends AnyMetaManager {


  override type MetaManagerCommand = BaseMetaManagerCommand

  override def initMessage(): BaseMetaManagerCommand = CreateNisperoWorkers(0)

 // val unDeployActionStarted = new AtomicBoolean(false)
 // val unDeployActionForce = new AtomicBoolean(false)

  override def process(command: BaseMetaManagerCommand,
                       env: MetaManagerEnvironment,
                       unDeployContext: MetaManagerUnDeployingActionContext,
                       controlQueueOp: AnyQueueOp,
                       queueOps: List[AnyQueueOp],
                       terminationDaemon: TerminationDaemon[MetaManagerEnvironment]
                       ): Try[List[BaseMetaManagerCommand]] = {
    val logger = env.logger
    logger.info("processing tasks " + command)
    command match {
      case CreateNisperoWorkers(index) if index < compota.nisperos.size => {
        val nispero = compota.nisperos(index)
        Try {
          compota.createNisperoWorkers(env, nispero)
        }.flatMap{e => e}.map { res =>
          List(CreateNisperoWorkers(index + 1))
        }
      }
      case CreateNisperoWorkers(index) => {
        Success(List(AddTasks))
      }
      case AddTasks => {
        logger.info("adding tasks?")
        compota.tasksAdded().flatMap { added =>
          if(added) {
            logger.warn("tasks has been already added")
            Success(List[BaseMetaManagerCommand]())
          } else {
            logger.info("adding tasks")
            compota.addTasks(env).flatMap { r =>
              compota.setTasksAdded()
            }.map { res =>
              List(LaunchTerminationDaemon)
            }
          }
        }
      }
      case LaunchTerminationDaemon => {
        compota.launchTerminationDaemon(terminationDaemon).map { t =>
          List[BaseMetaManagerCommand]()
        }
      }
      case UnDeploy(reason, force) => {
        logger.info("undeploying reason: " + reason + " force: " + force)
        Success(List(DeleteNisperoWorkers(0, reason, force)))
      }
      case DeleteNisperoWorkers(index, reason, force) if index < compota.nisperos.size => {
        val nispero = compota.nisperos(index)
        Try {
          compota.deleteNisperoWorkers(env, nispero)
        }.flatMap{e => e}.map { res =>
          List(DeleteNisperoWorkers(index + 1, reason, force))
        }
      }
      case DeleteNisperoWorkers(index, reason, true) => {
        Success(List(DeleteQueue(0, reason, true)))
      }
      case DeleteNisperoWorkers(index, reason, false) => {
        Success(List(ReduceQueue(0, reason)))
      }
      case ReduceQueue(index, reason) if index < compota.reducers.size => {
        val reducer = compota.reducers(index)
        Try {
          logger.info("reducing queue " + reducer.queue.name)
          reducer.reduce(env)
        }.flatMap{e => e}.map { res =>
          List(ReduceQueue(index + 1, reason))
        }
      }
      case ReduceQueue(index, reason) => {
        Success(List(DeleteQueue(0, reason, force = false)))
      }
      case DeleteQueue(index, reason, force) if index < queueOps.size => {
        logger.info("deleting queue " + queueOps(index).queue.name)
        Try {
          queueOps(index).delete()
        }.flatMap{e => e}.map { res =>
          List(DeleteQueue(index + 1, reason, force))
        }
      }
      case DeleteQueue(index, reason, force) => {
        Success(List(UnDeployActions(reason, force)))
      }

      case UnDeployActions(reason, true) => {
//        if(unDeployActionStarted.get() && unDeployActionForce.get()) {
//          logger.info("undeploy actions has been already started")
//          Success(List[BaseMetaManagerCommand]())
//        } else {
//          logger.info("running undeploy actions force=true")
//          unDeployActionForce.set(true)
//          unDeployActionStarted.set(true)

        //skip undeploy steps in case of error
          Try{compota.unDeployActions(true, env, unDeployContext)}.flatMap{e => e} match {
            case Success(message) => Success(List(FinishCompota(reason, message)))
            case Failure(t) => {
              logger.error(t)
              Success(List(FinishCompota(reason, t.toString)))
            }
          }
       // }
      }
      case UnDeployActions(reason, false) => {
//        if(unDeployActionStarted.get()) {
//          logger.info("undeploy actions has been already started")
//          Success(List[BaseMetaManagerCommand]())
//        } else {
//          logger.info("running undeploy actions")
//          unDeployActionStarted.set(true)
//          unDeployActionForce.set(false)

          Try{compota.unDeployActions(false, env, unDeployContext)}.flatMap{e => e}.map { message =>
            List(FinishCompota(reason, message))
          }
       // }
      }
      case FinishCompota(reason, message) => {
        compota.finishUnDeploy(env, reason, message).map { res =>
          List(UnDeployMetaManger)
        }
      }
      case UnDeployMetaManger => {
        Try {
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
