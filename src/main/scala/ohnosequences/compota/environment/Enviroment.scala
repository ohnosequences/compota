package ohnosequences.compota.environment

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}

import ohnosequences.compota.{AnyCompotaConfiguration, ErrorTable, Namespace}
import ohnosequences.logging.Logger

import java.io.File
import scala.util.{Success, Failure, Try}

case class InstanceId(id: String) {
  def subInstance(subId: String) = InstanceId(id + "_" + subId)
}

trait Env {
  val logger: Logger
  def isStopped: Boolean
  val workingDirectory: File
}

abstract class AnyEnvironment[E <: AnyEnvironment[E]] extends Env {

  def rootEnvironment: E

  val environments: ConcurrentHashMap[InstanceId, E]

  def subEnvironmentSync[R](suffix: String)(statement: E => R): Try[(E, R)]

  def subEnvironmentAsync(suffix: String)(statement: E => Unit): Try[E] = {
    subEnvironmentSync(suffix) { env =>
      executor.execute(new Runnable {
        override def run(): Unit = {
          val oldName = Thread.currentThread().getName
          Thread.currentThread().setName(suffix)
          env.logger.debug("changing thread name to " + instanceId.id)
          env.environments.put(instanceId, env)
          statement(env)
          Thread.currentThread().setName(oldName)
          env.environments.remove(env.instanceId)
        }
      })
    }.map(_._1)
  }

  def instanceId: InstanceId

  val errorTable: ErrorTable

  val executor: ExecutorService

  val configuration: AnyCompotaConfiguration

  def localErrorCounts: AtomicInteger

  def sendForceUnDeployCommand(reason: String, message: String): Try[Unit]

  def stop(): Unit

  protected def terminate(): Unit

  /**
   * all repeats are here, "5 errors - reboot,  10 errors - undeplot compota
   * shouldn't do nothing if env is terminated
   */
  def reportError(nameSpace: Namespace, t: Throwable): Unit = {
    val sb = new StringBuilder
    logger.printThrowable(t, { e =>
      sb.append(e)
      sb.append(System.lineSeparator())
    })
    val stackTrace = sb.toString()

    val localErrorCount = localErrorCounts.incrementAndGet()
     
    if (localErrorCount > math.min(configuration.errorThreshold, configuration.localErrorThreshold)) {
      errorTable.getNamespaceErrorCount(nameSpace) match {
        case Failure(tt) => {
          logger.error("couldn't retrieve count from error table")
          errorTable.recover()
          stop()
          terminate()
        }
        case Success(count) if count >= configuration.errorThreshold => {
          val message = "reached global error threshold for " + nameSpace.toString
          val fullMessage = message + System.lineSeparator() + stackTrace
          logger.error("reached global error threshold for " + nameSpace.toString)
          sendForceUnDeployCommand("error threshold reached", fullMessage)
        }
        case Success(count) => {
          logger.error("reached local error threshold for " + nameSpace.toString + " [" + count + "]")
          stop()
          terminate()
        }
      }
    } else {
      logger.error(nameSpace.toString + " failed " + localErrorCount + " times [" + configuration.localErrorThreshold + "/" + configuration.errorThreshold + "]")
      logger.debug(t)
      errorTable.reportError(nameSpace, System.currentTimeMillis(), instanceId, t.toString, sb.toString())
    }
  }

}

