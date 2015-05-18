package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.{ExecutorService, ConcurrentHashMap}
import ohnosequences.compota.{ErrorTable, Namespace}
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.logging.{FileLogger, Logger}

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.collection.JavaConversions._


class LocalEnvironment(val executor: ExecutorService,
                       val localErrorTable: LocalErrorTable,
                       val prefix: String,
                        val logger: FileLogger,
                        val workingDirectory: File,
                        val errorCounts: ConcurrentHashMap[String, Int],
                        val configuration: LocalCompotaConfiguration,
                        val sendUnDeployCommand0: (LocalEnvironment, String, Boolean) => Try[Unit]
                         ) extends AnyEnvironment { localEnvironment =>

  //type


  override val errorTable: ErrorTable = localErrorTable

  def instanceLogFile: File = {
    logger.logFile
  }

  val isStoppedFlag = new java.util.concurrent.atomic.AtomicBoolean(false)


  override def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit] = sendUnDeployCommand0(localEnvironment, reason, force)

  override val instanceId: InstanceId = InstanceId(prefix)

  //when fatal error occurs
  override def isStopped: Boolean = isStoppedFlag.get()

  //override val logger: Logger = new FileLogger("logger", logFile, debug = true, false)

  override def stop(): Unit ={
    //logger. flush????
    isStoppedFlag.set(true)
 //   thread.stop()
  }

  def getThreadInfo: Option[(Thread, Array[StackTraceElement])] = {
    Thread.getAllStackTraces.find { case (t, st) =>
      t.getName.eq(instanceId.id)
    }
  }


  override def terminate(): Unit = {
    //thread.stop
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

  def localContext: LocalContext = {
    new LocalContext(executor)
  }

}

object LocalEnvironment {
  def execute(instancesEnvironments: ConcurrentHashMap[InstanceId, (AnyLocalNispero, LocalEnvironment)],
              nispero: Option[AnyLocalNispero], //for workers only
              executor: ExecutorService,
              localErrorTable: LocalErrorTable,
              prefix: String,
              loggingDirectory: File,
              workingDirectory: File,
              configuration: LocalCompotaConfiguration,
              errorCount: ConcurrentHashMap[String, Int],
              sendUnDeployCommand: (LocalEnvironment, String, Boolean) => Try[Unit])(statement: LocalEnvironment => Unit): LocalEnvironment = {
    loggingDirectory.mkdir()
    workingDirectory.mkdir()

    val envLogger = new FileLogger(prefix, new File(loggingDirectory, prefix + ".log"), configuration.loggerDebug, printToConsole = true)
    val env: LocalEnvironment = new LocalEnvironment(executor, localErrorTable, prefix, envLogger, workingDirectory, errorCount, configuration, sendUnDeployCommand)

    executor.execute(new Runnable {
      override def run(): Unit = {
        nispero.foreach { nispero =>
          instancesEnvironments.put(env.instanceId, (nispero, env))
        }

        val oldName = Thread.currentThread().getName
        Thread.currentThread().setName(prefix)
        env.logger.info("changing thread to " + prefix)
        statement(env)

        Thread.currentThread().setName(oldName)
        env.logger.info("removing id " + prefix)
        instancesEnvironments.remove(env.instanceId)

      }
    })
    env
  }


}
