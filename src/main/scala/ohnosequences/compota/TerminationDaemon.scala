package ohnosequences.compota

import ohnosequences.compota.environment._
import ohnosequences.compota.graphs.NisperoGraph

import scala.annotation.tailrec
import scala.util.{Try, Success, Failure}

class TerminationDaemon(nisperoGraph: NisperoGraph, sendUnDeployCommand: (String, Boolean) => Try[Unit]) {
  def start(environment: AnyEnvironment): Try[Unit] = {

    @tailrec
    def startRec():  Try[Unit] = {
      if (environment.isTerminated) {
        Success(())
      } else {
        nisperoGraph.checkQueues(environment) match {
          case Failure(t) => {
            environment.reportError("check_queue", t) //todo repeats
          }
          case Success(Left(queueOp)) => {
            environment.logger.debug("queue " + queueOp.queue.name + " isn't empty")
          }
          case Success(Right(queues)) => {
            environment.logger.info("all queues are empty")
            //send undeploy command
            //todo check errors + add force stop timeout
            environment.logger.info("stoping termination daemon")
            environment.stop()
            sendUnDeployCommand("terminated", false)
          }
        }
        Thread.sleep(10000)
        startRec()
      }
    }

    startRec()
  }



}
