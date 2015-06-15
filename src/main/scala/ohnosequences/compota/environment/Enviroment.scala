package ohnosequences.compota.environment

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}

import ohnosequences.compota.{AnyCompotaConfiguration, ErrorTable, Namespace}
import ohnosequences.logging.Logger

import java.io.File
import scala.util.{Success, Failure, Try}

case class InstanceId(id: String) {
  override def toString: String = id
}
//{
//  def subInstance(subId: String) = InstanceId(id + "_" + subId)
//}

trait Env {
  val logger: Logger
  def isStopped: Boolean
  val workingDirectory: File
}

abstract class AnyEnvironment[E <: AnyEnvironment[E]] extends Env { anyEnvironment =>

  def rootEnvironment: E

  val environments: ConcurrentHashMap[(InstanceId, Namespace), E]

  def subEnvironmentSync[R](subspaceOrInstance: Either[String, InstanceId], async: Boolean)(statement: E => R): Try[(E, R)]

  def threadName: String = instanceId.id + "." + namespace.toString

  def subEnvironmentAsync(subspaceOrInstance: Either[String, InstanceId])(statement: E => Unit): Try[E] = {
    subEnvironmentSync(subspaceOrInstance, async = true) { env =>
      executor.execute(new Runnable {
        override def run(): Unit = {
          val oldName = Thread.currentThread().getName
          env.logger.debug("changing thread name from " + Thread.currentThread().getName + " to " + env.threadName)
          Thread.currentThread().setName(env.threadName)
          env.logger.debug("new name: " + Thread.currentThread().getName)
          Try {
            statement(env)
          }
          //env.reportError()
          env.logger.debug("finishing " + env.threadName)
          env.logger.debug("changing thread name to " + oldName)
          Thread.currentThread().setName(oldName)
          env.environments.remove((env.instanceId, env.namespace))
        }
      })
    }.map(_._1)
  }

  def instanceId: InstanceId

  def namespace: Namespace

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
  def reportError(t: Throwable, namespace: Namespace = namespace): Unit = {

//    val namespace = subSpace match {
//      case None => anyEnvironment.namespace
//      case Some(sub) => anyEnvironment.namespace / sub
//    }

    val sb = new StringBuilder
    logger.printThrowable(t, { e =>
      sb.append(e)
      sb.append(System.lineSeparator())
    })
    val stackTrace = sb.toString()

    val localErrorCount = localErrorCounts.incrementAndGet()
     
    if (localErrorCount > math.min(configuration.errorThreshold, configuration.localErrorThreshold)) {
      errorTable.getNamespaceErrorCount(namespace) match {
        case Failure(tt) => {
          logger.error("couldn't retrieve count from error table")
          errorTable.recover()
          stop()
          terminate()
        }
        case Success(count) if count >= configuration.errorThreshold => {
          val message = "reached global error threshold for " + namespace.toString
          val fullMessage = message + System.lineSeparator() + stackTrace
          logger.error("reached global error threshold for " + namespace.toString)
          sendForceUnDeployCommand("error threshold reached", fullMessage)
        }
        case Success(count) => {
          logger.error("reached local error threshold for " + namespace.toString + " [" + count + "]")
          stop()
          terminate()
        }
      }
    } else {
      logger.error(namespace.toString + " failed " + localErrorCount + " times [" + configuration.localErrorThreshold + "/" + configuration.errorThreshold + "]")
      logger.debug(t)
      errorTable.reportError(namespace, System.currentTimeMillis(), instanceId, t.toString, sb.toString())
    }
  }

}

