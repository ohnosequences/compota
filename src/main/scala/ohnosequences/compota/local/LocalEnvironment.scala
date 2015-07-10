package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, ConcurrentHashMap}
import ohnosequences.compota.Namespace
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.logging.{Logger, FileLogger}

import scala.util.{Success, Try}
import scala.collection.JavaConversions._


class LocalEnvironment(val instanceId: InstanceId,
                       val namespace: Namespace,
                       val workingDirectory: File,
                       val logger: FileLogger,
                       val executor: ExecutorService,
                       val errorTable: LocalErrorTable,
                       val configuration: AnyLocalCompotaConfiguration,
                       val sendForceUnDeployCommand0: (LocalEnvironment, String, String) => Try[Unit],
                       val environments: ConcurrentHashMap[(InstanceId, Namespace), LocalEnvironment],
                       val originEnvironment: Option[LocalEnvironment],
                       val localErrorCounts: AtomicInteger
                       ) extends AnyEnvironment[LocalEnvironment] { localEnvironment =>

  val isStoppedFlag = new java.util.concurrent.atomic.AtomicBoolean(false)

  override def prepareSubEnvironment[R](subspaceOrInstance: Either[String, InstanceId], async: Boolean)(statement: LocalEnvironment => R): Try[(LocalEnvironment, R)] = {
    Try {
      val (newInstance, newNamespace, newLogger, newWorkingDirectory) = subspaceOrInstance match {
        case Left(subspace) => {
          val nWorkingDirectory = new File(workingDirectory, subspace)
          logger.debug("creating working directory: " + nWorkingDirectory.getAbsolutePath)
          nWorkingDirectory.mkdir()
          val nLogger = logger.subLogger(subspace)
          (instanceId, namespace / subspace, nLogger, nWorkingDirectory)
        }
        case Right(instance) => {
          val nWorkingDirectory = new File(workingDirectory, instanceId.id)
          logger.debug("creating working directory: " + nWorkingDirectory.getAbsolutePath)
          nWorkingDirectory.mkdir()
          val nLogger = logger.subLogger(instance.id)
          (instance, Namespace.root, nLogger, nWorkingDirectory)
        }
      }
      new LocalEnvironment(
        instanceId = newInstance,
        namespace = newNamespace,
        workingDirectory = newWorkingDirectory,
        logger = newLogger,
        executor = executor,
        errorTable = errorTable,
        configuration = configuration,
        sendForceUnDeployCommand0 = sendForceUnDeployCommand0,
        environments = environments,
        originEnvironment = Some(localEnvironment),
        localErrorCounts = localErrorCounts
      )
    }.flatMap { env =>
      environments.put((env.instanceId, env.namespace), env)
      val res = Try {
        (env, statement(env))
      }
      if (!async) {
        environments.remove((env.instanceId, env.namespace))
      }
      res
    }
  }

  override def terminate(): Unit = {
    logger.error("LocalEnvironment#terminate is not implemented")
  }

  def localContext: LocalContext = new LocalContext(executor, logger)

  override def isStopped: Boolean = isStoppedFlag.get()

  override def stop(recursive: Boolean): Unit ={
    isStoppedFlag.set(true)
    if (recursive) {
      environments.foreach { case ((i, n), e) => e.stop(false)}
    }
  }

  def sendForceUnDeployCommand(reason: String, message: String): Try[Unit] = sendForceUnDeployCommand0(localEnvironment, reason, message)

  def getThreadInfo: Option[(Thread, Array[StackTraceElement])] = {
    Thread.getAllStackTraces.find { case (t, st) =>
     //logger.info("locking for " + threadName)
      t.getName.equals(threadName)
    } match {
      case None => {
        logger.debug("couldn't find " + threadName + " trying to find origin environment thread info: " + originEnvironment)
        originEnvironment.flatMap(_.getThreadInfo)
      }
      case success => success
    }
  }
}

