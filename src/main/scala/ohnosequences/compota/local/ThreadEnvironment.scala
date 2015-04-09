package ohnosequences.compota.local

import java.io.File
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.logging.{FileLogger, Logger}


class ThreadEnvironment(val thread: Thread, val logger: Logger, val workingDirectory: File) extends AnyEnvironment {

  //type


  override def start: Unit = {
    thread.start()
  }

  val isStopped = new java.util.concurrent.atomic.AtomicBoolean(false)


  override val instanceId: InstanceId = InstanceId(thread.getName)

  //when fatal error occurs
  override def isTerminated: Boolean = isStopped.get()

  //override val logger: Logger = new FileLogger("logger", logFile, debug = true, false)

  override def kill(): Unit ={
    isStopped.set(true)
 //   thread.stop()
  }

  override def reportError(taskId: String, t: Throwable): Unit = {
    logger.error(taskId + " failed")
    logger.error(t)
  }
}

object ThreadEnvironment {
  def execute(prefix: String, debug: Boolean, loggingDirectory: File, workingDirectory: File)(statement: ThreadEnvironment => Unit): ThreadEnvironment = {
    loggingDirectory.mkdir()
    workingDirectory.mkdir()
    var env: Option[ThreadEnvironment] = None
    val envLogger = new FileLogger(prefix, new File(loggingDirectory, prefix + ".log"), debug, printToConsole = true)
    object thread extends Thread(prefix) {
      env = Some(new ThreadEnvironment(this, envLogger, workingDirectory))
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
