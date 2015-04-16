package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.{TimeUnit, Executors, ConcurrentHashMap}
import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.local.metamanager.{LocalMetaManager}
import ohnosequences.compota.metamanager.{UnDeploy, BaseMetaManagerCommand}
import ohnosequences.compota.queues.{AnyQueueReducer, AnyQueueOp}
import ohnosequences.compota.{TerminationDaemon, Compota}
import ohnosequences.logging.FileLogger
import scala.collection.JavaConversions._
import scala.concurrent.duration._

import scala.util.{Success, Try}

abstract class LocalCompota[U](nisperos: List[AnyLocalNispero],
                            reducers: List[AnyQueueReducer.of[LocalEnvironment]],
                            val configuration: LocalCompotaConfiguration
                            ) extends
  Compota[LocalEnvironment, AnyLocalNispero, U](nisperos, reducers, configuration) { localCompota =>

  val isFinished = new java.util.concurrent.atomic.AtomicBoolean(false)

  val controlQueue = new LocalQueue[BaseMetaManagerCommand]("control_queue", visibilityTimeout = Duration(10, SECONDS))

  val executor = Executors.newCachedThreadPool()

  def waitForFinished(): Unit = {
    while(!isFinished.get()) {
      Thread.sleep(5000)
     // printThreads()
    }
  }

  val nisperoEnvironments = new ConcurrentHashMap[String, ConcurrentHashMap[String, LocalEnvironment]]()

  val errorCounts = new ConcurrentHashMap[String, Int]()

  nisperos.foreach { nispero =>
    nisperoEnvironments.put(nispero.name, new ConcurrentHashMap[String, LocalEnvironment]())
  }

  val metamanager = new LocalMetaManager(localCompota, nisperoEnvironments)


  override def launchWorker(nispero: AnyLocalNispero): Unit = {

    val workerDirectory = new File(configuration.workingDirectory, "worker_" + nispero.name)

    LocalEnvironment.execute(executor, "worker_" + nispero.name,
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

    val env = LocalEnvironment.execute(
      executor,
      prefix,
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

  def launchMetaManager(): Unit = {
    LocalEnvironment.execute(
      executor,
      "metamanager",configuration.workingDirectory,
      configuration.workingDirectory,
      configuration,
      errorCounts,
      sendUnDeployCommand
    ) { env =>
      env.logger.debug("launching metamanger")
      metamanager.launchMetaManager(env, controlQueue, {e: LocalEnvironment => e.localContext})
    }
  }


  override def launchTerminationDaemon(terminationDaemon: TerminationDaemon[CompotaEnvironment]): Try[Unit] = {
    LocalEnvironment.execute(
      executor,
      "termination daemon",
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

  def printThreads(): Unit = {
    Thread.getAllStackTraces.foreach { case (thread, sts) =>
      println(thread + ":" + thread.getState)
      sts.foreach { st =>
        println("      " + st.toString)
      }
    }
  }

  override def deleteManager(env: CompotaEnvironment): Try[Unit] = {

    env.executor.awaitTermination(10, TimeUnit.SECONDS)

//    val g = Thread.currentThread().getThreadGroup
//    val threads = Array.ofDim[Thread](g.activeCount())
//    g.enumerate(threads)

   // println(threads.toList)

    Success(())
  }

  override def launch(): Try[Unit] = {
    Try {
      launchMetaManager()
    }
  }

  override def sendUnDeployCommand(env: LocalEnvironment, reason: String, force: Boolean): Try[Unit] = {
    controlQueue.create(env.localContext).flatMap { queueOp =>
      queueOp.writer.flatMap { writer =>
        writer.writeRaw(List(("undeploy_" + reason + "_" + force, UnDeploy(reason, force))))
      }
    }
  }

  def finishUnDeploy(env: LocalEnvironment, reason: String, message: String): Try[Unit] = {

    Success(isFinished.set(true))
  }

  //override def unDeployActions(force: Boolean): Try[Unit] = ???
}
