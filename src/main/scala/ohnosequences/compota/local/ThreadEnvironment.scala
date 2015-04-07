package ohnosequences.compota.local

import java.io.File
import ohnosequences.compota.environment.{Environment, InstanceId}
import ohnosequences.logging.Logger


class ThreadEnvironment(thread: Thread, val logger: Logger) extends Environment[Unit] {

  //type

  val isStopped = new java.util.concurrent.atomic.AtomicBoolean(false)

  override def queueCtx: Unit = {}

  override val instanceId: InstanceId = InstanceId(thread.getName)

  //when fatal error occurs
  override def isTerminated: Boolean = isStopped.get()

  //override val logger: Logger = new FileLogger("logger", logFile, debug = true, false)

  override def kill(): Unit ={
    isStopped.set(true)
 //   thread.stop()
  }

  override def reportError(taskId: String, t: Throwable): Unit = {
    logger.error(taskId + " failed ")
    logger.error(t)
  }
}
