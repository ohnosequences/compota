package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.local.metamanager.{LocalMetaManager}
import ohnosequences.compota.metamanager.{UnDeploy, BaseMetaManagerCommand}
import ohnosequences.compota.queues.{AnyQueueReducer, AnyQueueOp}
import ohnosequences.compota.{TerminationDaemon, Compota}
import ohnosequences.logging.FileLogger
import scala.collection.JavaConversions._

import scala.util.{Success, Try}

abstract class LocalCompota[U](nisperos: List[AnyLocalNispero],
                            reducers: List[AnyQueueReducer.of[ThreadEnvironment]],
                            val configuration: LocalCompotaConfiguration
                            ) extends
  Compota[ThreadEnvironment, AnyLocalNispero, U](nisperos, reducers, configuration) { localCompota =>

  val isFinished = new java.util.concurrent.atomic.AtomicBoolean(false)

  val controlQueue = new LocalQueue[BaseMetaManagerCommand]("control_queue")

  def waitForFinished(): Unit = {
    while(!isFinished.get()) {
      Thread.sleep(1000)
    }
  }

  val nisperoEnvironments = new ConcurrentHashMap[String, ConcurrentHashMap[String, ThreadEnvironment]]()

  val errorCounts = new ConcurrentHashMap[String, Int]()

  nisperos.foreach { nispero =>
    nisperoEnvironments.put(nispero.name, new ConcurrentHashMap[String, ThreadEnvironment]())
  }

  val metamanager = new LocalMetaManager(localCompota, nisperoEnvironments)


  override def launchWorker(nispero: AnyLocalNispero): Unit = {

    val workerDirectory = new File(configuration.workingDirectory, "worker_" + nispero.name)

    ThreadEnvironment.execute("worker_" + nispero.name,
      configuration.workingDirectory,
      workerDirectory,
      configuration,
      errorCounts,
      sendUnDeployCommand) { env =>
      nispero.createWorker().start(env)
    }
  }

  def launchWorker(nispero: AnyLocalNispero, i: Int): Unit = {

    val prefix = "worker_" + nispero.name + "_" + i

    val workerDirectory = new File(configuration.workingDirectory, prefix)

    val env = ThreadEnvironment.execute(prefix,
      configuration.workingDirectory,
      workerDirectory,
      configuration,
      errorCounts,
      sendUnDeployCommand
      ) { env =>
      nispero.createWorker().start(env)
    }
    nisperoEnvironments.get(nispero.name).put(prefix, env)
  }

  def launchMetamanager(): Unit = {
    ThreadEnvironment.execute("metamanager",configuration.workingDirectory,
      configuration.workingDirectory,
      configuration,
      errorCounts,
      sendUnDeployCommand
    ) { env =>
      env.logger.debug("launching metamanger")
      metamanager.launchMetaManager(env, controlQueue, {e: ThreadEnvironment => ()})
    }
  }


  override def launchTerminationDaemon(terminationDaemon: TerminationDaemon): Try[Unit] = {
    ThreadEnvironment.execute("termination daemon",
      configuration.workingDirectory,
      configuration.workingDirectory,
      configuration,
      errorCounts,
      sendUnDeployCommand
    ) { env =>
      env.logger.debug("launching termination daemon")
      terminationDaemon.start(env)
    }
    Success(())
  }


  override def startedTime(): Try[Long] = Success(System.currentTimeMillis())

  val tasksAdded_ = new AtomicBoolean(false)

  override def tasksAdded(): Try[Boolean] = {
    Success(tasksAdded_.get)
  }


  override def setTasksAdded(): Try[Unit] = {
    Success(tasksAdded_.set(true))
  }

  override def createNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit] = {
    Try {
      for (i <- 1 to nispero.workers) {
        launchWorker(nispero, i)
      }
    }
  }

  override def deleteNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit] = {

    env.logger.info("stopping " + nispero.name + " nispero")
    //logger.debug("envs: " + )
    Try {
      nisperoEnvironments.get(nispero.name).foreach { case (id, e) =>
        env.logger.debug("stopping environment: " + id)
        e.stop()
      }
    }
  }

  override def deleteManager(env: CompotaEnvironment): Try[Unit] = {
    Success(())
  }

  override def launch(): Try[Unit] = {
    Try {
      launchMetamanager()
    }
  }

  override def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit] = {
    controlQueue.create(()).flatMap { queueOp =>
      queueOp.writer.flatMap { writer =>
        writer.writeRaw(List(("undeploy_" + reason + "_" + force, UnDeploy(reason, force))))
      }
    }
  }

  def finishUnDeploy(reason: String, message: String): Try[Unit] = {
    Success(isFinished.set(true))
  }

  //override def unDeployActions(force: Boolean): Try[Unit] = ???
}
