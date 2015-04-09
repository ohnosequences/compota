package ohnosequences.compota.local

import java.io.File

import ohnosequences.compota.queues.{AnyQueueReducer, AnyQueueOp}
import ohnosequences.compota.{Compota}
import ohnosequences.logging.FileLogger

import scala.util.Try

abstract class LocalCompota(nisperos: List[AnyLocalNispero],
                            reducers: List[AnyQueueReducer.of[ThreadEnvironment]],
                            configuration: LocalCompotaConfiguration
                            ) extends
  Compota[ThreadEnvironment, AnyLocalNispero](nisperos, reducers) {

  override def createNispero(nispero: Nispero): Try[Unit] = ???

  override def deleteNispero(nispero: Nispero): Unit = ???

  override def deleteQueue(queue: AnyQueueOp): Unit = ???



  override def launchWorker(nispero: AnyLocalNispero): Unit = {

    val workerDirectory = new File(configuration.workingDirectory, "worker_" + nispero.name)

    ThreadEnvironment.execute("worker_" + nispero.name, configuration.loggerDebug, configuration.workingDirectory, workerDirectory) { env =>
      nispero.createWorker().start(env)
    }
  }

  def launchWorker(nispero: AnyLocalNispero, i: Int): Unit = {

    val prefix = "worker_" + nispero.name + "_" + i

    val workerDirectory = new File(configuration.workingDirectory, prefix)

    ThreadEnvironment.execute(prefix, configuration.loggerDebug, configuration.workingDirectory, workerDirectory) { env =>
      nispero.createWorker().start(env)
    }
  }

  override def addTasks(): Unit = {
    ThreadEnvironment.execute("add_tasks", configuration.loggerDebug, configuration.workingDirectory, configuration.workingDirectory) { env =>
      env.logger.info("adding tasks")
      addTasks(env)
    }
  }

  override def launch(): Try[Unit] = {
    Try {
      addTasks()
      nisperos.foreach { nispero =>
        for (i <- 1 to nispero.workers) {
          launchWorker(nispero, i)
        }
      }
    }
  }
}
