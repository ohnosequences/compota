package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, ConcurrentHashMap}
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.logging.{Logger, FileLogger}

import scala.util.{Success, Try}
import scala.collection.JavaConversions._


class LocalEnvironment(val instanceId: InstanceId,
                       val workingDirectory: File,
                       val logger: FileLogger,
                       val executor: ExecutorService,
                       val errorTable: LocalErrorTable,
                       val configuration: AnyLocalCompotaConfiguration,
                       val sendForceUnDeployCommand0: (LocalEnvironment, String, String) => Try[Unit],
                       val environments: ConcurrentHashMap[InstanceId, LocalEnvironment],
                       val rootEnvironment0: Option[LocalEnvironment],
                       val origin: Option[LocalEnvironment],
                       val localErrorCounts: AtomicInteger
                       ) extends AnyEnvironment[LocalEnvironment] { localEnvironment =>

  val isStoppedFlag = new java.util.concurrent.atomic.AtomicBoolean(false)

  override def rootEnvironment: LocalEnvironment = rootEnvironment0 match {
    case None => localEnvironment
    case Some(env) => env
  }


  override def subEnvironmentSync[R](id: String)(statement: LocalEnvironment => R): Try[(LocalEnvironment, R)] = {
    Try {
      val newWorkingDirectory = new File(workingDirectory, id)
      newWorkingDirectory.mkdir()
      val env = new LocalEnvironment(
        instanceId = instanceId.subInstance(id),
        workingDirectory = newWorkingDirectory,
        logger = logger.subLogger(id, true),
        executor = executor,
        errorTable = errorTable,
        configuration = configuration,
        sendForceUnDeployCommand0 = sendForceUnDeployCommand0,
        environments = environments,
        rootEnvironment0 = Some(rootEnvironment),
        origin = Some(localEnvironment),
        localErrorCounts = localErrorCounts
      )
      (env, statement(env))
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

  def sendForceUnDeployCommand(reason: String, message: String): Try[Unit] = sendForceUnDeployCommand0(localEnvironment, reason, message)

  def getThreadInfo: Option[(Thread, Array[StackTraceElement])] = {
    Thread.getAllStackTraces.find { case (t, st) =>
      t.getName.equals(instanceId.id)
    }
  }
}

//object LocalEnvironment {
//
//  def execute(instanceId: InstanceId,
//              workingDirectory: File,
//              instancesEnvironments: ConcurrentHashMap[InstanceId, (Option[AnyLocalNispero], LocalEnvironment)],
//              nispero: Option[AnyLocalNispero],
//              executor: ExecutorService,
//              localErrorTable: LocalErrorTable,
//              configuration: AnyLocalCompotaConfiguration,
//              sendForceUnDeployCommand: (LocalEnvironment, String, String) => Try[Unit])(statement: LocalEnvironment => Unit): Try[LocalEnvironment] = {
//
//
//    Success(()).flatMap { u =>
//      configuration.workingDirectory.mkdir()
//      configuration.loggingDirectory.mkdir()
//      val loggerDirectory = new File(configuration.loggingDirectory, instanceId.id)
//      FileLogger.apply(
//        instanceId.id,
//        new File(configuration.loggingDirectory, instanceId.id),
//        "log.txt",
//        configuration.loggerDebug,
//        printToConsole = true
//      ).map { envLogger =>
//
//        val workingDirectory = new File(configuration.workingDirectory, instanceId.id)
//        workingDirectory.mkdir()
//
//
//        val env: LocalEnvironment = new LocalEnvironment(
//          instanceId,
//          workingDirectory,
//          executor,
//          localErrorTable,
//          envLogger,
//          configuration,
//          sendForceUnDeployCommand,
//          instancesEnvironments,
//          None
//        )
//
//        executor.execute(new Runnable {
//          override def run(): Unit = {
//            instancesEnvironments.put(env.instanceId, (nispero, env))
//            val oldName = Thread.currentThread().getName
//            Thread.currentThread().setName(instanceId.id)
//            env.logger.debug("changing thread to " + instanceId.id)
//            statement(env)
//            Thread.currentThread().setName(oldName)
//            instancesEnvironments.remove(env.instanceId)
//          }
//        })
//        env
//      }
//    }
//  }
//}
