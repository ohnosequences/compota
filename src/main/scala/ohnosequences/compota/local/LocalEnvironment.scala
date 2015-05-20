package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.{ExecutorService, ConcurrentHashMap}
import ohnosequences.compota.{ErrorTable, Namespace}
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.logging.{FileLogger, Logger}

import scala.concurrent.ExecutionContext
import scala.util.{Success, Try}
import scala.collection.JavaConversions._


class LocalEnvironment(val instanceId: InstanceId,
                       val workingDirectory: File,
                       val executor: ExecutorService,
                       val localErrorTable: LocalErrorTable,
                       val logger: FileLogger,
                       val errorCounts: ConcurrentHashMap[String, Int],
                       val configuration: LocalCompotaConfiguration,
                       val sendUnDeployCommand0: (LocalEnvironment, String, Boolean) => Try[Unit],
                       val isStoppedFlag: java.util.concurrent.atomic.AtomicBoolean,
                       val instancesEnvironments: ConcurrentHashMap[InstanceId, (AnyLocalNispero, LocalEnvironment)],
                       val origin: Option[LocalEnvironment]
                       ) extends AnyEnvironment[LocalEnvironment] { localEnvironment =>




  override def subEnvironmentSync[R](suffix: String)(statement: LocalEnvironment => R): Try[(LocalEnvironment, R)] = {
    Try {
      val env = new LocalEnvironment(
        instanceId,
        new File(workingDirectory, suffix),
        executor,
        localErrorTable,
        logger.subLogger(suffix, true),
        errorCounts,
        configuration,
        sendUnDeployCommand0,
        isStoppedFlag,
        instancesEnvironments,
        Some(LocalEnvironment.this)
      )
      (env, statement(env))
    }
  }


  override def subEnvironment(suffix: String)(statement: (LocalEnvironment) => Unit): Try[LocalEnvironment] = {
    subEnvironmentSync(suffix) { env =>
      executor.execute(new Runnable {
        override def run(): Unit = {

          val oldName = Thread.currentThread().getName
          Thread.currentThread().setName(suffix)
          env.logger.debug("changing thread to " + instanceId.id)

          statement(env)

          Thread.currentThread().setName(oldName)
          env.instancesEnvironments.remove(env.instanceId)
        }
      })
    }.map(_._1)
  }

  def localContext: LocalContext = new LocalContext(executor, logger)

  override val errorTable: ErrorTable = localErrorTable

  override def isStopped: Boolean = isStoppedFlag.get()

  override def stop(): Unit ={
    origin.foreach(_.stop())
    isStoppedFlag.set(true)
 //   thread.stop()
  }

  override def terminate(): Unit = {

  }

  override def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit] = sendUnDeployCommand0(localEnvironment, reason, force)

  def getThreadInfo: Option[(Thread, Array[StackTraceElement])] = {
    Thread.getAllStackTraces.find { case (t, st) =>
      t.getName.equals(instanceId.id)
    }
  }


  def reportError(nameSpace: Namespace, t: Throwable): Unit = {
    val e = errorCounts.getOrDefault(nameSpace.toString, 0) + 1
    if (e > configuration.errorThreshold) {
      logger.error("reached error threshold for " + nameSpace.toString)
      sendUnDeployCommand("reached error threshold for " + nameSpace.toString, true)
    } else {
      errorCounts.put(nameSpace.toString, e)
      logger.error(nameSpace.toString + " failed " + e + " times")
      logger.debug(t)
    }
  }
}

object LocalEnvironment {

  def execute(instanceId: InstanceId,
              workingDirectory: File,
              instancesEnvironments: ConcurrentHashMap[InstanceId, (AnyLocalNispero, LocalEnvironment)],
              nispero: Option[AnyLocalNispero],
              executor: ExecutorService,
              localErrorTable: LocalErrorTable,
              configuration: LocalCompotaConfiguration,
              errorCount: ConcurrentHashMap[String, Int],
              sendUnDeployCommand: (LocalEnvironment, String, Boolean) => Try[Unit])(statement: LocalEnvironment => Unit): Try[LocalEnvironment] = {

    Success(()).flatMap { u =>

      configuration.loggingDirectory.mkdir()
      val loggerDirectory = new File(configuration.loggingDirectory, instanceId.id)
      FileLogger.apply(
        instanceId.id,
        new File(configuration.loggingDirectory, instanceId.id),
        "log.txt",
        configuration.loggerDebug,
        printToConsole = true
      ).map { envLogger =>

        val workingDirectory = new File(configuration.workingDirectory, instanceId.id)
        workingDirectory.mkdir()


        val env: LocalEnvironment = new LocalEnvironment(
          instanceId,
          workingDirectory,
          executor,
          localErrorTable,
          envLogger,
          errorCount,
          configuration,
          sendUnDeployCommand,
          new java.util.concurrent.atomic.AtomicBoolean(false),
          instancesEnvironments,
          None
        )

        executor.execute(new Runnable {
          override def run(): Unit = {
            nispero.foreach { nispero =>
              instancesEnvironments.put(env.instanceId, (nispero, env))
            }

            val oldName = Thread.currentThread().getName
            Thread.currentThread().setName(instanceId.id)
            env.logger.debug("changing thread to " + instanceId.id)

            statement(env)

            Thread.currentThread().setName(oldName)
            instancesEnvironments.remove(env.instanceId)
          }
        })
        env
      }
    }
  }
}
