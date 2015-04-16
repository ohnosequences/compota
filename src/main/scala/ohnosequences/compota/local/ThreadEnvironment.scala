package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.{ExecutorService, ConcurrentHashMap}
import ohnosequences.compota.Namespace
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.logging.{FileLogger, Logger}

import scala.concurrent.ExecutionContext
import scala.util.Try


class LocalEnvironment(val executor: ExecutorService,
                        val logger: Logger,
                        val workingDirectory: File,
                        val errorCounts: ConcurrentHashMap[String, Int],
                        val configuration: LocalCompotaConfiguration,
                        val sendUnDeployCommand0: (LocalEnvironment, String, Boolean) => Try[Unit]
                         ) extends AnyEnvironment { localEnvironment =>

  //type


  val isStoppedFlag = new java.util.concurrent.atomic.AtomicBoolean(false)


  override def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit] = sendUnDeployCommand0(localEnvironment, reason, force)

  override val instanceId: InstanceId = InstanceId(Thread.currentThread().getName)

  //when fatal error occurs
  override def isStopped: Boolean = isStoppedFlag.get()

  //override val logger: Logger = new FileLogger("logger", logFile, debug = true, false)

  override def stop(): Unit ={
    isStoppedFlag.set(true)
 //   thread.stop()
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
  def execute(executor: ExecutorService,
              prefix: String,
              loggingDirectory: File,
              workingDirectory: File,
              configuration: LocalCompotaConfiguration,
              errorCount: ConcurrentHashMap[String, Int],
              sendUnDeployCommand: (LocalEnvironment, String, Boolean) => Try[Unit])(statement: LocalEnvironment => Unit): LocalEnvironment = {
    loggingDirectory.mkdir()
    workingDirectory.mkdir()

    val envLogger = new FileLogger(prefix, new File(loggingDirectory, prefix + ".log"), configuration.loggerDebug, printToConsole = true)
    val env: LocalEnvironment = new LocalEnvironment(executor, envLogger, workingDirectory, errorCount, configuration, sendUnDeployCommand)

    executor.execute(new Runnable {
      override def run(): Unit = statement(env)
    })
    env
  }


}
