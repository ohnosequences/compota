package ohnosequences.compota.local

import java.io.File
import java.net.URL
import java.util.concurrent.{TimeUnit, Executors, ConcurrentHashMap}
import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.console.AnyConsole
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.local.metamanager.{LocalMetaManager}
import ohnosequences.compota.metamanager.{UnDeploy, BaseMetaManagerCommand}
import ohnosequences.compota.queues.{AnyQueueReducer, AnyQueueOp}
import ohnosequences.compota.{AnyCompota, TerminationDaemon, Compota}
import ohnosequences.logging.FileLogger
import scala.collection.JavaConversions._
import scala.concurrent.duration._

import scala.util.{Failure, Success, Try}

abstract class LocalCompota[U](nisperos: List[AnyLocalNispero],
                            reducers: List[AnyQueueReducer.of[LocalEnvironment]],
                            val localConfiguration: LocalCompotaConfiguration
                            ) extends
  Compota[LocalEnvironment, AnyLocalNispero, U](nisperos, reducers, localConfiguration) { localCompota =>

  val isFinished = new java.util.concurrent.atomic.AtomicBoolean(false)

  val controlQueue = new LocalQueue[BaseMetaManagerCommand]("control_queue", visibilityTimeout = Duration(10, SECONDS))

  val executor = Executors.newCachedThreadPool()

  val errorTable = new LocalErrorTable

  def waitForFinished(): Unit = {
    while(!isFinished.get()) {
      Thread.sleep(5000)
     // printThreads()
    }
  }





  //val nisperoEnvironments = new ConcurrentHashMap[String, ConcurrentHashMap[String, LocalEnvironment]]()
  val instancesEnvironments = new ConcurrentHashMap[InstanceId, (AnyLocalNispero, LocalEnvironment)]()

  def terminateInstance(instanceId: InstanceId): Try[Unit] = {
    Option(instancesEnvironments.get(instanceId)) match {
      case None => Success(()) //so idempotent
      case Some((nispero, env)) => {
        env.stop()
        instancesEnvironments.remove(instanceId)
        Success(())
      }
    }
  }

  val errorCounts = new ConcurrentHashMap[String, Int]()

  val metamanager = new LocalMetaManager(localCompota, instancesEnvironments)

  def getInstanceLog(instanceId: InstanceId): Try[Either[URL, String]] = {
    Option(instancesEnvironments.get(instanceId)) match {
      case None => Failure(new Error("instance " + instanceId.id + " not found"))
      case Some((nispero, env)) => {
        Try {
          val log = scala.io.Source.fromFile(env.instanceLogFile).getLines().mkString
          Right(log)
        }
      }
    }
  }

  override def launchWorker(nispero: AnyLocalNispero): Unit = {

    val workerDirectory = new File(localConfiguration.workingDirectory, "worker_" + nispero.configuration.name)

    LocalEnvironment.execute(
      instancesEnvironments,
      Some(nispero),
      executor,
      errorTable,
      "worker_" + nispero.configuration.name,
      localConfiguration.workingDirectory,
      workerDirectory,
      localConfiguration,
      errorCounts,
      sendUnDeployCommand) { env =>
      nispero.createWorker().start(env)
    }
  }



  def launchWorker(nispero: AnyLocalNispero, i: Int): Unit = {

    val prefix = "worker_" + nispero.configuration.name + "_" + i

    val workerDirectory = new File(localConfiguration.workingDirectory, prefix)

    val env = LocalEnvironment.execute(
      instancesEnvironments,
      Some(nispero),
      executor,
      errorTable,
      prefix,
      localConfiguration.workingDirectory,
      workerDirectory,
      localConfiguration,
      errorCounts,
      sendUnDeployCommand
      ) { env =>
      nispero.createWorker().start(env)
    }


  }

  def launchMetaManager(): Unit = {
    LocalEnvironment.execute(
      instancesEnvironments,
      None,
      executor,
      errorTable,
      "metamanager",localConfiguration.workingDirectory,
      localConfiguration.workingDirectory,
      localConfiguration,
      errorCounts,
      sendUnDeployCommand
    ) { env =>
      env.logger.debug("launching metamanger")
      metamanager.launchMetaManager(env, controlQueue, {e: LocalEnvironment => e.localContext})
    }
  }


  override def launchTerminationDaemon(terminationDaemon: TerminationDaemon[CompotaEnvironment]): Try[Unit] = {
    LocalEnvironment.execute(
      instancesEnvironments,
      None,
      executor,
      errorTable,
      "termination daemon",
      localConfiguration.workingDirectory,
      localConfiguration.workingDirectory,
      localConfiguration,
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
      for (i <- 1 to nispero.localConfiguration.workers) {
        launchWorker(nispero, i)
      }
    }
  }

  override def deleteNisperoWorkers(env: CompotaEnvironment, nispero: Nispero): Try[Unit] = {

    env.logger.info("stopping " + nispero.configuration.name + " nispero")
    //logger.debug("envs: " + )
    Try {
      instancesEnvironments.foreach { case (id, (n, e)) =>
        if (n.configuration.name.equals(nispero.configuration.name)) {
          env.logger.debug("stopping environment: " + id)
          e.stop()

        }
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

    //Success(isFinished.set(true))
    Success(())
  }





  //override def unDeployActions(force: Boolean): Try[Unit] = ???
  override def getConsoleInstance(nisperoGraph: NisperoGraph, env: CompotaEnvironment): AnyConsole = {
    new LocalConsole[U](LocalCompota.this, env, nisperoGraph)
  }


}
