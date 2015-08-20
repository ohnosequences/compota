package ohnosequences.compota

import ohnosequences.logging.ConsoleLogger


class TerminationDaemon(nisperon: Compota) extends Thread {

  val logger = new ConsoleLogger("termination daemon")

  val initLaunchTime = {

    var ititTime = 0L
    var attempt = 10
    while (ititTime == 0 && attempt > 0) {
      try {
        attempt -= 1
        ititTime = nisperon.launchTime

      } catch {
        case t: Throwable => t.printStackTrace()
      }

    }
    ititTime

  }
  val t0 = System.currentTimeMillis() / 1000

  def launchTime(): Long = {
    val t1 = System.currentTimeMillis() / 1000
    initLaunchTime + (t1 - t0)
  }

  override def run() {

    logger.info("termination daemon started")
    var stopped = false

    while (!stopped) {


      if (nisperon.compotaConfiguration.autoTermination) {
        logger.info("checking queues")
        nisperon.checkQueues() match {
          case Left(queue) => logger.info(queue.name + " isn't empty")
          case Right(queues) => {
            logger.info("all queues are empty. terminating")
            nisperon.sendUndeployCommand("solved", force = false)
            stopped = true
          }
        }
      }

      if (launchTime() > nisperon.compotaConfiguration.timeout) {
        logger.info("terminating due to timeout")
        nisperon.sendUndeployCommand("timeout " + nisperon.compotaConfiguration.timeout + " sec", force = true)
        stopped = true
      }

      Thread.sleep(10000)
    }
  }
}
