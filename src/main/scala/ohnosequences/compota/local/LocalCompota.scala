package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import ohnosequences.compota.local.metamanager.{LocalCommand, LocalMetaManager, UnDeploy}
import ohnosequences.compota.queues.{AnyQueueReducer, AnyQueueOp}
import ohnosequences.compota.{Compota}
import ohnosequences.logging.FileLogger

import scala.util.{Success, Try}

abstract class LocalCompota(nisperos: List[AnyLocalNispero],
                            reducers: List[AnyQueueReducer.of[ThreadEnvironment]],
                            configuration: LocalCompotaConfiguration
                            ) extends
  Compota[ThreadEnvironment, AnyLocalNispero](nisperos, reducers) {

  val isFinished = new java.util.concurrent.atomic.AtomicBoolean(false)

  val controlQueue = new LocalQueue[LocalCommand]("control_queue")

  def waitForFinished(): Unit = {
    while(!isFinished.get()) {
      Thread.sleep(1000)
    }
  }

  val nisperosEnvironments = new ConcurrentHashMap[String, ConcurrentHashMap[String, ThreadEnvironment]]()

  nisperos.foreach { nispero =>
    nisperosEnvironments.put(nispero.name, new ConcurrentHashMap[String, ThreadEnvironment]())
  }

  val metamanager = new LocalMetaManager(nisperos, reducers, nisperosEnvironments, unDeployActions, finishUnDeploy)


  override def launchWorker(nispero: AnyLocalNispero): Unit = {

    val workerDirectory = new File(configuration.workingDirectory, "worker_" + nispero.name)

    ThreadEnvironment.execute("worker_" + nispero.name, configuration.loggerDebug, configuration.workingDirectory, workerDirectory) { env =>
      nispero.createWorker().start(env)
    }
  }

  def launchWorker(nispero: AnyLocalNispero, i: Int): Unit = {

    val prefix = "worker_" + nispero.name + "_" + i

    val workerDirectory = new File(configuration.workingDirectory, prefix)

    val env = ThreadEnvironment.execute(prefix, configuration.loggerDebug, configuration.workingDirectory, workerDirectory) { env =>
      nispero.createWorker().start(env)
    }
    nisperosEnvironments.get(nispero.name).put(prefix, env)
  }

  override def addTasks(): Unit = {
    ThreadEnvironment.execute("add_tasks", configuration.loggerDebug, configuration.workingDirectory, configuration.workingDirectory) { env =>
      env.logger.info("adding tasks")
      addTasks(env)
    }
  }

  def launchMetamanager(): Unit = {
    ThreadEnvironment.execute("metamanager", configuration.loggerDebug, configuration.workingDirectory, configuration.workingDirectory) { env =>
      env.logger.debug("launching metamanger")
      metamanager.launchMetaManager(env, controlQueue, {e: ThreadEnvironment => ()})
    }
  }

  def launchTerminationDaemon(): Unit = {
    ThreadEnvironment.execute("termination_daemon", configuration.loggerDebug, configuration.workingDirectory, configuration.workingDirectory) { env =>
      env.logger.debug("launching termination daemon")
      launchTerminationDaemon(env)
    }
  }

  override def launch(): Try[Unit] = {
    Try {
      addTasks()
      nisperos.foreach { nispero =>
        for (i <- 1 to nispero.workers) {
          launchWorker(nispero, i)
        }
      }
      launchMetamanager()
      launchTerminationDaemon()
    }
  }

  override def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit] = {
    controlQueue.create(()).flatMap { queueOp =>
      queueOp.writer.flatMap { writer =>
        writer.writeRaw(List(("und", UnDeploy(reason, force))))
      }
    }
  }

  override def finishUnDeploy(): Try[Unit] = {
    Success(isFinished.set(true))
  }

  //override def unDeployActions(force: Boolean): Try[Unit] = ???
}
