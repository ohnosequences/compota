package ohnosequences.compota

import ohnosequences.compota.environment._
import ohnosequences.compota.graphs.{QueueChecker, NisperoGraph}
import ohnosequences.compota.Namespace._


import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.{Try, Success, Failure}

class TerminationDaemon[E <: AnyEnvironment[E]](compota: AnyCompota.ofE[E], queueChecker: QueueChecker[E]) {
  def start(env: E): Try[Unit] = {

    compota.startedTime(env).map { startedTime =>

      def isTimeoutReached: Boolean = {
        (System.currentTimeMillis() - startedTime) > compota.configuration.timeout.toMillis
      }

      @tailrec
      def messageLoop(): Try[Unit] = {
        if (env.isStopped) {
          Success(())
        } else {
          Thread.sleep(compota.configuration.terminationDaemonIdleTime.toMillis)
          if (isTimeoutReached) {
            val message = "reached compota timeout: " + compota.configuration.timeout + " mins"
            env.logger.info(message)
            env.logger.info("force undeploy")
            compota.sendForceUnDeployCommand(env, "timeout", message).recoverWith { case t =>
              env.reportError( new Error("couldn't send force undeploy command", t), env.namespace / "send_force_undeploy")
                Failure(t)
            }.map { sent =>
              env.stop()
            }

          } else {
            compota.compotaUnDeployActionContext.get() match {
              case None => {
                env.logger.debug("undeploy context is not ready")
              }
              case Some(context) => {
                queueChecker.checkQueues() match {
                  case Failure(t) => {
                    env.reportError(new Error("couldn't check queues", t), env.namespace / "check_queues")
                  }
                  case Success(Left(queueOp)) => {
                    env.logger.info("queue " + queueOp.queue.name + " isn't empty")
                  }
                  case Success(Right(queues)) => {
                    env.logger.info("all queues are empty")
                    env.logger.info("sending undeploy command")
                    compota.sendUnDeployCommand(env).recoverWith { case t =>
                      env.reportError(new Error("couldn't send undeploy command", t), env.namespace / "send_undeploy")
                      Failure(t)
                    }
                  }
                }
              }
            }
          }
          messageLoop()
        }
      }
      messageLoop()
      env.logger.info("termination daemon stopped")
    }
  }
}
