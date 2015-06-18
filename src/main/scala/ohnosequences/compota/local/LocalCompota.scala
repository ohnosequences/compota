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
import ohnosequences.logging.{ConsoleLogger, FileLogger, Logger}
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

import scala.util.{Failure, Success, Try}

trait AnyLocalCompota extends AnyCompota { anyLocalCompota =>

  override type CompotaEnvironment = LocalEnvironment
  override type CompotaNispero = AnyLocalNispero

  override type CompotaMetaManager = LocalMetaManager[CompotaUnDeployActionContext]
  override type CompotaConfiguration <: AnyLocalCompotaConfiguration

  val isFinished = new java.util.concurrent.atomic.AtomicBoolean(false)

  def waitForFinished(): Unit = {
    while(!isFinished.get()) {
      Thread.sleep(5000)
      // printThreads()
    }
  }


  override def initialEnvironment: Try[LocalEnvironment] = {
    val environments = new ConcurrentHashMap[(InstanceId, Namespace), LocalEnvironment]()
    configuration.workingDirectory.mkdir()
    FileLogger(configuration.initialEnvironmentId.id,
      configuration.loggingDirectory,
      "log.txt",
      configuration.loggerDebug,
      printToConsole = configuration.loggersPrintToConsole
    ).map { logger =>
      val env = new LocalEnvironment(
        instanceId = configuration.initialEnvironmentId,
        namespace = Namespace.root,
        workingDirectory = configuration.workingDirectory,
        logger = logger,
        executor = Executors.newCachedThreadPool(),
        errorTable = new LocalErrorTable(),
        configuration = configuration,
        sendForceUnDeployCommand0 = sendForceUnDeployCommand,
        environments = environments,
        originEnvironment = None,
        localErrorCounts = new AtomicInteger(0)
      )
      environments.put((env.instanceId, env.namespace), env)
      Thread.currentThread().setName(env.threadName)
      //initialEnvironmentRef.se
      env.logger.info("initial environment started")
      env
    }
  }


  override def sendNotification(env: LocalEnvironment, subject: String, message: String): Try[Unit] = {
    env.logger.info("sending notification")
    env.logger.info("sending notification. subject: " + subject)
    env.logger.info("sending notification. message: " + message.take(200))
    Success(())
  }

  //to start compota
  override def localEnvironment(cliLogger: ConsoleLogger, args: List[String]): Try[LocalEnvironment] = initialEnvironment

  def terminateInstance(env: CompotaEnvironment, instance: InstanceId, namespace: Namespace): Try[Unit] = {
    Option(env.environments.get((instance, namespace))) match {
      case None => Success(()) //so idempotent
      case Some(e) => Try(e.stop(false))
    }
  }

  override val metaManager = new LocalMetaManager[CompotaUnDeployActionContext](anyLocalCompota)

  def getLog(env: CompotaEnvironment, instanceId: InstanceId, namespace: Namespace): Try[String] = {
    env.logger.info("looking for namespace " + namespace.toString)
   // logger.info("known instances: " + environments.keys().toList)
    Option(env.environments.get((instanceId, namespace))) match {
      case None => Failure(new Error("instance :" + instanceId.id + " with namespace: " + namespace.toString + " not found"))
      case Some(e) => {
        Try {
          val log = scala.io.Source.fromFile(e.logger.logFile).getLines().mkString(System.lineSeparator())
          log
        }
      }
    }
  }


  private def workerSubspace(nispero: AnyLocalNispero, id: Int): String = {
    "worker_" + nispero.configuration.name + "_" + id
  }


  private def workerInstanceNamespace(nispero: AnyLocalNispero, id: Int): (InstanceId, Namespace) = {
    val s = workerSubspace(nispero, id)
    (InstanceId(s), Namespace.root)
  }

  def launchWorker(env: CompotaEnvironment, nispero: AnyLocalNispero, id: Int): Try[LocalEnvironment] = {
    val subId = workerSubspace(nispero, id)
    env.subEnvironmentAsync(Right(InstanceId(subId))) { env =>
      nispero.worker.start(env)
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
        launchWorker(env, nispero, i).get
      }
    }
  }

  override def deleteNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[Unit] = {
    env.logger.info("stopping " + nispero.configuration.name + " nispero")
    //logger.debug("envs: " + )
    Try {
      for (i <- 1 to nispero.configuration.workers) {
        terminateInstance(env, workerInstanceNamespace(nispero, i)._1, workerInstanceNamespace(nispero, i)._2).get
      }
    }
  }

  def listNisperoWorkers(env: CompotaEnvironment, nispero: CompotaNispero): Try[List[CompotaEnvironment]] = {
    Try {
      val res = new ListBuffer[CompotaEnvironment]()
      for (i <- 1 to nispero.configuration.workers) {
        env.logger.info("looking for " + workerInstanceNamespace(nispero, i))
        //logger.info("in " + environments)
        Option(env.environments.get(workerInstanceNamespace(nispero, i))).foreach { e =>
          res += e
        }
      }
      res.toList
    }
  }

  def getStackTrace(env: CompotaEnvironment, instance: InstanceId, namespace: Namespace): Try[String] = {
    Option(env.environments.get((instance, namespace))) match {
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

  override def launch(env: CompotaEnvironment): Try[CompotaEnvironment] = {
    println("launching metamanager")
    Try {metaManager.launchMetaManager(env)}.recoverWith { case t =>
      t.printStackTrace()
      Failure(t)
    }.map { u => env}
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
    nisperos.foreach { nispero =>
      Try {
        deleteNisperoWorkers(env, nispero)
      }
      Try {
        env.logger.warn("deleting queue: " + nispero.inputQueue.name)
        nispero.inputQueue.delete(nispero.inputContext(env))
      }
      Try {
        env.logger.warn("deleting queue: " + nispero.outputQueue.name)
        nispero.outputQueue.delete(nispero.outputContext(env))
      }
    }
    env.logger.warn("force undeploy")
    env.logger.info("forcedUnDeployActions")
    val m2 = Success(()).flatMap { u => forcedUnDeployActions(env) } match {
      case Success(m) => env.logger.info("forcedUnDeployActions result: " + m); m
      case Failure(t) => env.logger.warn(t); ""
    }
    Try {
      sendNotification(env, configuration.name + " terminated", "reason: " + reason + System.lineSeparator() + message + System.lineSeparator() + m2)
    }
    Try {
      deleteManager(env)
    }
    env.stop(recursive = true)
    Success(isFinished.set(true))

  }

  override def launchConsole(nisperoGraph: QueueChecker[CompotaEnvironment], controlQueueOp: AnyQueueOp, env: CompotaEnvironment): Try[AnyConsole] = {
    Try{
      val console = new LocalConsole[CompotaNispero](AnyLocalCompota.this, env, controlQueueOp, nisperoGraph)
      val server = new UnfilteredConsoleServer(console, "localhost")
      val message = server.startedMessage(customMessage)
      sendNotification(env, configuration.name + " started", message)
      server.start()
      console
    }
  }

}

object AnyLocalCompota {
  type of[U] = AnyLocalCompota { type CompotaUnDeployActionContext = U}

  type of2[N <: AnyLocalNispero] = AnyLocalCompota { type CompotaNispero = N}
}

abstract class LocalCompota[U](val nisperos: List[AnyLocalNispero],
                            val configuration: AnyLocalCompotaConfiguration
                            ) extends AnyLocalCompota {

 // override type CompotaNispero = AnyLocalNispero
  override type CompotaConfiguration = AnyLocalCompotaConfiguration
  override type CompotaUnDeployActionContext = U

}
