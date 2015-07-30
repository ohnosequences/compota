package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, ConcurrentHashMap}
import ohnosequences.compota.{ErrorTable, Namespace}
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.compota.metamanager.ForceUnDeploy
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
                       val forceUnDeploy0: (LocalEnvironment, String, String) => Try[Unit],
                       val environments: ConcurrentHashMap[(InstanceId, Namespace), LocalEnvironment],
                       val originEnvironment: Option[LocalEnvironment],
                       val localErrorCounts: AtomicInteger
                       ) extends AnyEnvironment[LocalEnvironment] { localEnvironment =>

  val isStoppedFlag = new java.util.concurrent.atomic.AtomicBoolean(false)


  override val localErrorTable: ErrorTable = new LocalErrorTable

  override def subEnvironment(subspace: String): Try[LocalEnvironment] = {
    Try {
      val newWorkingDirectory = new File(workingDirectory, subspace)
      logger.debug("creating working directory: " + newWorkingDirectory.getAbsolutePath)
      newWorkingDirectory.mkdir()
      new LocalEnvironment(
        instanceId = instanceId,
        namespace = namespace / subspace,
        workingDirectory = newWorkingDirectory,
        logger = logger.subLogger(subspace),
        executor = executor,
        errorTable = errorTable,
        configuration = configuration,
        forceUnDeploy0 = forceUnDeploy0,
        environments = environments,
        originEnvironment = Some(localEnvironment),
        localErrorCounts = localErrorCounts
      )
    }
  }

  def subEnvironment(instanceId: InstanceId): Try[LocalEnvironment] = {
    Try {
      val newWorkingDirectory = new File(configuration.workingDirectory, instanceId.id)
      logger.debug("creating working directory: " + newWorkingDirectory.getAbsolutePath)
      newWorkingDirectory.mkdir()

      val newLogger = FileLogger(instanceId.id,
        new File(configuration.loggingDirectory, instanceId.id),
        "log.txt",
        configuration.loggerDebug,
        printToConsole = configuration.loggersPrintToConsole
      ).get

      new LocalEnvironment(
        instanceId = instanceId,
        namespace = Namespace.root,
        workingDirectory = newWorkingDirectory,
        logger = newLogger,
        executor = executor,
        errorTable = errorTable,
        configuration = configuration,
        forceUnDeploy0 = forceUnDeploy0,
        environments = environments,
        originEnvironment = Some(localEnvironment),
        localErrorCounts = localErrorCounts
      )
    }
  }

  override def terminate(): Unit = {
    logger.error("LocalEnvironment#terminate is not implemented")
  }

  def localContext: LocalContext = new LocalContext(executor, logger)

  override def isStopped: Boolean = isStoppedFlag.get()

  override def stop(): Unit ={
    isStoppedFlag.set(true)
  }

  def forceUndeploy(reason: String, message: String): Try[Unit] = forceUnDeploy0(localEnvironment, reason, message)

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

