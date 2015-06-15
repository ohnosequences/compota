package ohnosequences.compota.local


import java.util.concurrent.{TimeUnit, Executors, ConcurrentHashMap}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference, AtomicBoolean}

import ohnosequences.compota.console.{UnfilteredConsoleServer, AnyConsole}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.{QueueChecker}
import ohnosequences.compota.local.metamanager.{LocalMetaManager}
import ohnosequences.compota.metamanager.{ForceUnDeploy, UnDeploy}
import ohnosequences.compota.queues.{AnyQueueOp, AnyQueueReducer}
import ohnosequences.compota.{Namespace, AnyCompota}
import ohnosequences.logging.{FileLogger, Logger}
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

import scala.util.{Failure, Success, Try}

trait AnyLocalCompota extends AnyCompota { anyLocalCompota =>

  override type CompotaEnvironment = LocalEnvironment
  override type CompotaNispero = AnyLocalNispero

  override type CompotaMetaManager = LocalMetaManager[CompotaUnDeployActionContext]
  override type CompotaConfiguration <: AnyLocalCompotaConfiguration

  val isFinished = new java.util.concurrent.atomic.AtomicBoolean(false)

  val executor = Executors.newCachedThreadPool()

  val errorTable = new LocalErrorTable

  def waitForFinished(): Unit = {
    while(!isFinished.get()) {
      Thread.sleep(5000)
      // printThreads()
    }
  }

  val environments = new ConcurrentHashMap[(InstanceId, Namespace), LocalEnvironment]()

  override lazy val initialEnvironment: Try[LocalEnvironment] = {
    FileLogger(configuration.initialEnvironmentId.id,
      configuration.loggingDirectory,
      "log.txt",
      configuration.loggerDebug,
      printToConsole = configuration.loggersPrintToConsole
    ).map { logger =>
      configuration.workingDirectory.mkdir()
      val env = new LocalEnvironment(
        instanceId = configuration.initialEnvironmentId,
        namespace = Namespace.root,
        workingDirectory = configuration.workingDirectory,
        logger = logger,
        executor = executor,
        errorTable = errorTable,
        configuration = configuration,
        sendForceUnDeployCommand0 = sendForceUnDeployCommand,
        environments = environments,
        rootEnvironment0 = None,
        origin = None,
        localErrorCounts = new AtomicInteger(0)
      )
      environments.put((env.instanceId, env.namespace), env)
      //initialEnvironmentRef.se
      env.logger.info("initial environment started")
      env
    }
  }

  def terminateInstance(instance: InstanceId): Try[Unit] = {
    environments.find { case ((inst, ns), env) =>
      instance.id.equals(inst.id)
    } match {
      case None => Success(()) //so idempotent
      case Some(((inst, ns), env)) => {
        env.stop()
        Success(())
      }
    }
  }

  override val metaManager = new LocalMetaManager[CompotaUnDeployActionContext](anyLocalCompota)

  def getLog(logger: Logger, instanceId: InstanceId, namespace: Namespace): Try[String] = {
    logger.info("looking for namespace " + namespace.toString)
   // logger.info("known instances: " + environments.keys().toList)
    Option(environments.get((instanceId, namespace))) match {
      case None => Failure(new Error("instance :" + instanceId.id + " with namespace: " + namespace.toString + " not found"))
      case Some(env) => {
        Try {
          val log = scala.io.Source.fromFile(env.logger.logFile).getLines().mkString(System.lineSeparator())
          log
        }
      }
    }
  }


  private def workerSubspace(nispero: AnyLocalNispero, id: Int): String = {
    "worker_" + nispero.configuration.name + "_" + id
  }

//  private def workerNamespace(nispero: AnyLocalNispero, id: Int): Namespace = {
//    Namespace.root / workerSubspace(nispero, id)
//  }

  private def workerInstanceNamespace(nispero: AnyLocalNispero, id: Int): (InstanceId, Namespace) = {
    val s = workerSubspace(nispero, id)
    (InstanceId(s), Namespace.root / s)
  }

  def launchWorker(nispero: AnyLocalNispero, id: Int): Try[LocalEnvironment] = {
    val subId = workerSubspace(nispero, id)
    initialEnvironment.flatMap { iEnv =>
      iEnv.subEnvironmentAsync(Right(InstanceId(subId))) { env =>
        nispero.worker.start(env)
      }
    }
  }


  override def startedTime(env: CompotaEnvironment): Try[Long] = Success(System.currentTimeMillis())

  val compotaDeployed_ = new AtomicBoolean(false)
  override def compotaDeployed(env: CompotaEnvironment): Try[Boolean] = {
    Success(compotaDeployed_.get)
  }


  override def createNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit] = {
    Try {
      for (i <- 1 to nispero.configuration.workers) {
        launchWorker(nispero, i).get
      }
    }
  }

  override def deleteNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit] = {
    env.logger.info("stopping " + nispero.configuration.name + " nispero")
    //logger.debug("envs: " + )
    Try {
      for (i <- 1 to nispero.configuration.workers) {
        terminateInstance(workerInstanceNamespace(nispero, i)._1).get
      }
    }
  }

  def listNisperoWorkers(nispero: CompotaNispero): Try[List[CompotaEnvironment]] = {
    Try {
      val res = new ListBuffer[CompotaEnvironment]()
      for (i <- 1 to nispero.configuration.workers) {
        Option(environments.get(workerInstanceNamespace(nispero, i))).foreach { e =>
          res += e
        }
      }
      res.toList
    }
  }

  def printThreads(): Unit = {
    Thread.getAllStackTraces.foreach { case (thread, sts) =>
      println(thread.toString + ":" + thread.getState)
      sts.foreach { st =>
        println("      " + st.toString)
      }
    }
  }

  def getStackTrace(instance: InstanceId, namespace: Namespace): Try[String] = {
    Option(environments.get((instance, namespace))) match {
      case None => Failure(new Error("instance " + instance.id + " with namespace " + namespace.toString + " does not exist"))
      case Some(e) => {
        e.getThreadInfo match {
          case None => Failure(new Error("couldn't get stack trace for instance: " + instance.id + " namespace: " + namespace.toString))
          case Some((t, a)) => {
            val stackTrace = new StringBuffer()
            a.foreach { s =>
              stackTrace.append("at " + s.toString + System.lineSeparator())
            }
            Success(stackTrace.toString)
          }
        }
      }
    }
  }



  override def deleteManager(env: CompotaEnvironment): Try[Unit] = {
    env.executor.awaitTermination(10, TimeUnit.SECONDS)
    Success(())
  }

  override def launch(): Try[CompotaEnvironment] = {
    //println("ge")
    launchMetaManager()
  }


  def finishUnDeploy(env: LocalEnvironment, reason: String, message: String): Try[Unit] = {
    Success(isFinished.set(true))
  }




  //send command to metamanager that reduce queues etc
  override def sendUnDeployCommand(env: CompotaEnvironment): Try[Unit] = {
    metaManager.sendMessageToControlQueue(env, UnDeploy)
  }

  override def sendForceUnDeployCommand(env: CompotaEnvironment, reason: String, message: String): Try[Unit] = {
    metaManager.sendMessageToControlQueue(env, ForceUnDeploy(reason, message))
  }

  //undeploy right now
  override def forceUnDeploy(env: CompotaEnvironment, reason: String, message: String): Try[Unit] = {
    Success(()).flatMap { u =>
      env.logger.warn("force undeploy")
      env.logger.info("forcedUnDeployActions")
      forcedUnDeployActions(env).flatMap { message =>
        env.logger.info("forcedUnDeployActions result: " + message)
        finishUnDeploy(env, reason, message)
      }
    }
  }

  override def launchConsole(nisperoGraph: QueueChecker[CompotaEnvironment], controlQueueOp: AnyQueueOp, env: CompotaEnvironment): Try[AnyConsole] = {
    Try{
      val console = new LocalConsole[CompotaNispero](AnyLocalCompota.this, env, controlQueueOp, nisperoGraph)
      new UnfilteredConsoleServer(console).start()
      console
    }
  }

}

object AnyLocalCompota {
  type of[U] = AnyLocalCompota { type CompotaUnDeployActionContext = U}

  type of2[N <: AnyLocalNispero] = AnyLocalCompota { type CompotaNispero = N}
}

abstract class LocalCompota[U](val nisperos: List[AnyLocalNispero],
                            val reducers: List[AnyQueueReducer.of[LocalEnvironment]],
                            val configuration: AnyLocalCompotaConfiguration
                            ) extends AnyLocalCompota {

 // override type CompotaNispero = AnyLocalNispero
  override type CompotaConfiguration = AnyLocalCompotaConfiguration
  override type CompotaUnDeployActionContext = U

}
