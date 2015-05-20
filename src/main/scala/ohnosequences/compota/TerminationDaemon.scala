package ohnosequences.compota

import ohnosequences.compota.environment._
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota.Namespace._


import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.{Try, Success, Failure}

class TerminationDaemon[E <: AnyEnvironment[E]](nisperoGraph: NisperoGraph,
                        sendUnDeployCommand: (E, String, Boolean) => Try[Unit],
                        startedTime: Long,
                        timeout: Duration,
                        terminationDaemonIdleTime: Duration) {
  def start(environment: E): Try[Unit] = {

    def isTimeoutReached: Boolean = {
      (System.currentTimeMillis() - startedTime) > timeout.toMillis
    }

    @tailrec
    def startRec(): Try[Unit] = {

      if (isTimeoutReached) {
        environment.logger.info("reached compota timeout: " + timeout.toMinutes + " mins")
        environment.logger.info("sending undeploy command")
        sendUnDeployCommand(environment, "timeout", true).recoverWith[Unit] { case t =>
          environment.reportError(terminationDaemon / "send_undeploy_command", new Error("couldn't send undeploy command", t))
          Failure[Unit](t)
        }
        environment.logger.info("stopping termination daemon")
        environment.stop()
        Success[Unit](())
      } else if (environment.isStopped) {
        Success(())
      } else {
        nisperoGraph.checkQueues(environment) match {
          case Failure(t) => {
            environment.reportError(terminationDaemon / "check_queues", new Error("couldn't check queues", t))
          }
          case Success(Left(queueOp)) => {
            environment.logger.debug("queue " + queueOp.queue.name + " isn't empty")
          }
          case Success(Right(queues)) => {
            environment.logger.info("all queues are empty")

            environment.logger.info("sending undeploy command")
            sendUnDeployCommand(environment, "terminated", false).recoverWith { case t =>
              environment.reportError(terminationDaemon / "send_undeploy_command", new Error("couldn't send undeploy command", t))
              Failure(t)
            }
            environment.logger.info("stopping termination daemon")
            environment.stop()
          }
        }
        Thread.sleep(terminationDaemonIdleTime.toMillis)
        startRec()
      }
    }

    startRec()
  }
}
