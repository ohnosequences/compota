package ohnosequences.compota.local

import java.io.File
import java.net.URL
import java.util.concurrent.{TimeUnit, Executors, ConcurrentHashMap}
import java.util.concurrent.atomic.AtomicBoolean

import ohnosequences.compota.console.{UnfilteredConsoleServer, AnyConsole}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.local.metamanager.{LocalMetaManager}
import ohnosequences.compota.metamanager.{UnDeploy, BaseMetaManagerCommand}
import ohnosequences.compota.queues.{AnyQueueReducer}
import ohnosequences.compota.{AnyCompota, TerminationDaemon}
import scala.collection.JavaConversions._
import scala.concurrent.duration._

import scala.util.{Failure, Success, Try}

trait AnyLocalCompota extends AnyCompota {
  override type CompotaEnvironment = LocalEnvironment
  override type Nispero <: AnyLocalNispero
  override type MetaManager = LocalMetaManager[CompotaUnDeployActionContext]

  override type CompotaConfiguration <: LocalCompotaConfiguration

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

  val instancesEnvironments = new ConcurrentHashMap[InstanceId, (AnyLocalNispero, LocalEnvironment)]()

  def terminateInstance(instanceId: InstanceId): Try[Unit] = {
    Option(instancesEnvironments.get(instanceId)) match {
      case None => Success(()) //so idempotent
      case Some((nispero, env)) => {
        env.stop()
        Success(())
      }
    }
  }

  val errorCounts = new ConcurrentHashMap[String, Int]()

  val metamanager = new LocalMetaManager[CompotaUnDeployActionContext](AnyLocalCompota.this, instancesEnvironments)

  def getInstanceLog(instanceId: InstanceId): Try[String] = {
    Option(instancesEnvironments.get(instanceId)) match {
      case None => Failure(new Error("instance " + instanceId.id + " not found"))
      case Some((nispero, env)) => {
        Try {
          val log = scala.io.Source.fromFile(env.logger.logFile).getLines().mkString(System.lineSeparator())
          log
        }
      }
    }
  }

  //not needed for local compota
  override def launchWorker(nispero: Nispero): Try[LocalEnvironment] = ???


  def launchWorker(nispero: AnyLocalNispero, id: Int): Try[LocalEnvironment] = {
    val prefix = "worker_" + nispero.configuration.name + "_" + id

    LocalEnvironment.execute(
      InstanceId(prefix),
      new File(configuration.workingDirectory, prefix),
      instancesEnvironments,
      Some(nispero),
      executor,
      errorTable,
      configuration,
      errorCounts,
      sendUnDeployCommand
    ) { env =>
      nispero.createWorker().start(env)
    }
  }

  def launchMetaManager(): Try[LocalEnvironment] = {
    val prefix = "metamanager"
    LocalEnvironment.execute(
      InstanceId(prefix),
      new File(configuration.workingDirectory, prefix),
      instancesEnvironments,
      None,
      executor,
      errorTable,
      configuration,
      errorCounts,
      sendUnDeployCommand
    ) { env =>
      env.logger.debug("launching metamanger")
      metamanager.launchMetaManager(env, controlQueue, {e: LocalEnvironment => e.localContext}, {launchTerminationDaemon}, {launchConsole})
    }
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
    Success(())
  }

  override def launch(): Try[Unit] = {
    launchMetaManager().map { env => ()}
  }

  override def sendUnDeployCommand(env: CompotaEnvironment, reason: String, force: Boolean): Try[Unit] = {
    controlQueue.create(env.localContext).flatMap { queueOp =>
      queueOp.writer.flatMap { writer =>
        writer.writeRaw(List(("undeploy_" + reason + "_" + force, UnDeploy(reason, force))))
      }
    }
  }

  def finishUnDeploy(env: LocalEnvironment, reason: String, message: String): Try[Unit] = {
    Success(isFinished.set(true))
    //Success(())
  }


  override def launchConsole(nisperoGraph: NisperoGraph, env: CompotaEnvironment): Try[AnyConsole] = {
    Try{
      val console = new LocalConsole[Nispero](AnyLocalCompota.this, env, nisperoGraph)
      new UnfilteredConsoleServer(console).start()
      console
    }
  }

}

object AnyLocalCompota {
  type of[U] = AnyLocalCompota { type CompotaUnDeployActionContext = U}

  type of2[N <: AnyLocalNispero] = AnyLocalCompota { type Nispero = N}
}

abstract class LocalCompota[U](val nisperos: List[AnyLocalNispero],
                            val reducers: List[AnyQueueReducer.of[LocalEnvironment]],
                            val configuration: LocalCompotaConfiguration
                            ) extends AnyLocalCompota {

  override type CompotaConfiguration = LocalCompotaConfiguration
  override type CompotaEnvironment = LocalEnvironment
  override type Nispero = AnyLocalNispero
  override type CompotaUnDeployActionContext = U

}
