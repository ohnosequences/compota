package ohnosequences.compota.environment

import ohnosequences.logging.{ConsoleLogger, Logger}


class ThreadEnvironment(thread: Thread) extends Environment[Unit] {

  //type

  val isStopped = new java.util.concurrent.atomic.AtomicBoolean(false)

  override def queueCtx: Unit = {}

  override val instanceId: InstanceId = InstanceId(thread.getName)

  //when fatal error occurs
  override def isTerminated: Boolean = isStopped.get()

  override val logger: Logger = new ConsoleLogger("logger")

  override def kill(): Unit ={
    isStopped.set(true)
 //   thread.stop()
  }

  override def reportError(taskId: String, t: Throwable): Unit = {
    logger.error(taskId + " failed ")
    logger.error(t)
  }
}
