package ohnosequences.compota.local.metamanager

import java.util.concurrent.ConcurrentHashMap

import ohnosequences.compota.metamanager.AnyMetaManager
import ohnosequences.compota.queues.AnyQueueReducer

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import ohnosequences.compota.local.{AnyLocalNispero, ThreadEnvironment}

import scala.util.{Failure, Success, Try}


class LocalMetaManager(
                        nisperos: List[AnyLocalNispero],
                        reducers: List[AnyQueueReducer.of[ThreadEnvironment]],
                        nisperoEnvironments: ConcurrentHashMap[String, ConcurrentHashMap[String, ThreadEnvironment]],
                        unDeployActions: (Boolean,ThreadEnvironment) => Try[Unit],
                        finishUnDeploy: => Try[Unit]
                        ) extends AnyMetaManager {

  override type MetaManagerEnvironment = ThreadEnvironment
  override type MetaManagerCommand = LocalCommand

  val isUnDeployActionStarted = new java.util.concurrent.atomic.AtomicBoolean(false)

  override def process(command: LocalCommand, env: ThreadEnvironment): Try[List[LocalCommand]] = {

    //@tailrec
    def processRec(command: LocalCommand, env: ThreadEnvironment): Try[List[LocalCommand]] =
    {
      val logger = env.logger
      command match {
        case UnDeploy(reason, force) => {
          logger.info("undeploying reason: " + reason + " force: " + force)
          Success(List(StopNispero(0)))
        }
        case StopNispero(nisperoId) => {
          if (nisperoId < nisperos.size) {
            val nispero = nisperos(nisperoId)
            logger.info("stopping " + nispero.name + " nispero")
            //logger.debug("envs: " + )
            nisperoEnvironments.get(nispero.name).foreach { case (id, e) =>
              logger.debug("stopping environment: " + id)
              e.stop()
            }
            Success(List(StopNispero(nisperoId + 1)))
          } else {
            Success(List(LaunchReducer(0)))
          }
        }

        case LaunchReducer(reducerId) => {
          if (reducerId < reducers.size) {
            val reducer = reducers(reducerId)
            logger.info("launching reducer for queue: " + reducer.queue.name)
            logger.debug("preparing queue context")
            val context = reducer.context(env)
            reducer.reduce(env).map { u: Unit =>
              List(LaunchReducer(reducerId + 1))
            }
          } else {
            Success(List(UnDeployActions(force = false)))
          }
        }

        case UnDeployActions(force) => {
          if (isUnDeployActionStarted.get()) {
            logger.warn("undeploy action has been already started")
            Success(List[LocalCommand]())
          } else {
            isUnDeployActionStarted.set(true)
            logger.info("running undeploy actions")
            unDeployActions(force, env).map { u: Unit =>
              List(FinishUndeploy)
            }
          }
        }
        case FinishUndeploy => {
          logger.info("finishing compota")
          finishUnDeploy.map { u: Unit =>
            List[LocalCommand]()
          }
        }
        case c => {
          Failure(new Error("wrong command: " + c))
        }
      }
    }

    processRec(command, env)
  }


}
