package ohnosequences.compota.enviroment

import ohnosequences.compota.logging.{ConsoleLogger, Logger}


class ThreadInstance extends Instance {
  override def getId: InstanceId = InstanceId(Thread.currentThread().getName)

  //when fatal error occurs
  override def isTerminated: Boolean = false

  override def getLogger: Logger = new ConsoleLogger

  override def kill(): Unit = Thread.currentThread().stop()

  override def fatalError(failure: Throwable): Unit = {failure.printStackTrace()}
}
