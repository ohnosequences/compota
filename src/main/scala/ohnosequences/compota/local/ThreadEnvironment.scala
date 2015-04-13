package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import ohnosequences.compota.Namespace
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.logging.{FileLogger, Logger}

import scala.util.Try


class ThreadEnvironment(val thread: Thread,
                        val logger: Logger,
                        val workingDirectory: File,
                        val errorCounts: ConcurrentHashMap[String, Int],
                        val configuration: LocalCompotaConfiguration,
                        val sendUnDeployCommand0: (String, Boolean) => Try[Unit]
                         ) extends AnyEnvironment {

  //type



  val isStopped = new java.util.concurrent.atomic.AtomicBoolean(false)


  override def sendUnDeployCommand(reason: String, force: Boolean): Try[Unit] = sendUnDeployCommand0(reason, force)

  override val instanceId: InstanceId = InstanceId(thread.getName)

  //when fatal error occurs
  override def isTerminated: Boolean = isStopped.get()

  //override val logger: Logger = new FileLogger("logger", logFile, debug = true, false)

  override def stop(): Unit ={
    isStopped.set(true)
 //   thread.stop()
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

object ThreadEnvironment {
  def execute(prefix: String,
              loggingDirectory: File,
              workingDirectory: File,
              configuration: LocalCompotaConfiguration,
              errorCount: ConcurrentHashMap[String, Int],
              sendUnDeployCommand: (String, Boolean) => Try[Unit])(statement: ThreadEnvironment => Unit): ThreadEnvironment = {
    loggingDirectory.mkdir()
    workingDirectory.mkdir()
    var env: Option[ThreadEnvironment] = None
    val envLogger = new FileLogger(prefix, new File(loggingDirectory, prefix + ".log"), configuration.loggerDebug, printToConsole = true)
    object thread extends Thread(prefix) {
      env = Some(new ThreadEnvironment(this, envLogger, workingDirectory, errorCount, configuration, sendUnDeployCommand))
      override def run(): Unit ={
        env match {
          case Some(e) =>  statement(e)
          case None => envLogger.error("initialization error")
        }
      }
    }
    thread.start()
    env match {
      case Some(e) =>  e
      case None => throw new Error("initialization error")
    }
  }


}
