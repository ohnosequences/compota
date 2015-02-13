package ohnosequences.compota.environment

import ohnosequences.logging.{ConsoleLogger, Logger}


class ThreadEnvironment(thread: Thread) extends Environment[Unit] {

  //type

  override def queueCtx: Unit = {}

  override val instanceId: InstanceId = InstanceId(thread.getName)

  //when fatal error occurs
  override def isTerminated: Boolean = false

  override val logger: Logger = new ConsoleLogger("logger")

  override def kill(): Unit ={
    thread.stop()
  }

  override def reportError(taskId: String, t: Throwable): Unit = {
    logger.error(taskId + " failed ")
    logger.error(t)
  }
}
