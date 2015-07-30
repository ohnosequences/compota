package ohnosequences.compota.environment

import java.util.concurrent.{ConcurrentHashMap, ExecutorService}

import ohnosequences.compota.{AnyCompotaConfiguration, ErrorTable, Namespace}
import ohnosequences.logging.Logger

import java.io.File
import scala.util.{Success, Failure, Try}

case class InstanceId(id: String) {
  override def toString: String = id
}


trait Env {
  val logger: Logger

  def isStopped: Boolean

  val workingDirectory: File
}

object Env {
  def apply(logger0: Logger): Env = new Env {
    override def isStopped: Boolean = false

    override val workingDirectory: File = new File(".")
    override val logger: Logger = logger0
  }
}

abstract class AnyEnvironment[E <: AnyEnvironment[E]] extends Env {
  anyEnvironment =>

  def originEnvironment: Option[E]

  val environments: ConcurrentHashMap[(InstanceId, Namespace), E]

  def subEnvironment(subspace: String): Try[E]

  def subEnvironmentSync[R](subspace: String)(statement: E => R): Try[(E, R)] = {
    subEnvironment(subspace).flatMap { env =>
      runSubEnvironmentSync(env)(statement)
    }
  }

  def runSubEnvironmentSync[R](env: E)(statement: E => R): Try[(E, R)] = {
    environments.put((env.instanceId, env.namespace), env)
    val res = Try {
      (env, statement(env))
    }
    environments.remove((env.instanceId, env.namespace))
    res
  }

  def runSubEnvironmentAsync(env: E)(statement: E => Unit): Try[E] = {
    Try {
      executor.execute(new Runnable {
        override def run(): Unit = {
          val oldName = Thread.currentThread().getName
          env.logger.debug("changing thread name from " + Thread.currentThread().getName + " to " + env.threadName)
          Thread.currentThread().setName(env.threadName)
          env.logger.debug("new thread name: " + Thread.currentThread().getName)
          environments.put((env.instanceId, env.namespace), env)
          val res = Try {
            (env, statement(env))
          }
          environments.remove((env.instanceId, env.namespace))
          env.logger.debug("environment " + env.threadName + " finished")
          env.logger.debug("changing thread name to " + oldName)
          Thread.currentThread().setName(oldName)
        }
      })
      env
    }
  }

  def threadName: String = instanceId.id + "." + namespace.toString

  def subEnvironmentAsync(subspace: String)(statement: E => Unit): Try[E] = {
    subEnvironment(subspace).flatMap { env =>
      runSubEnvironmentAsync(env)(statement)
    }
  }

  def instanceId: InstanceId

  def namespace: Namespace

  val localErrorTable: ErrorTable

  val errorTable: ErrorTable

  val executor: ExecutorService

  val configuration: AnyCompotaConfiguration

  def forceUndeploy(reason: String, message: String): Try[Unit]

  def stop(): Unit

  def terminate(): Unit

  /**
   * all repeats are here, "5 errors - reboot,  10 errors - undeplot compota
   * shouldn't do nothing if env is terminated
   */
  def reportError(t: Throwable, namespace: Namespace = namespace): Unit = {

    val sb = new StringBuilder
    logger.printThrowable(t, { e =>
      sb.append(e)
      sb.append(System.lineSeparator())
    })
    val stackTrace = sb.toString()

    errorTable.getNamespaceErrorCount(namespace) match {
      case Failure(tt) => {
        logger.error("couldn't retrieve counts from error table")
        errorTable.recover()
        stop()
        terminate()
      }
      case Success(count) if count >= configuration.globalErrorThreshold => {
        val message = "reached global error threshold for " + namespace.toString
        val fullMessage = message + System.lineSeparator() + stackTrace
        logger.error("reached global error threshold for " + namespace.toString)
        logger.error(t)
        forceUndeploy("error threshold reached", fullMessage)
      }
      case Success(count) => {
        localErrorTable.getNamespaceErrorCount(namespace) match {
          case Failure(lt) => {
            val message = "couldn't retrieve error count from local table"
            val fullMessage = message + System.lineSeparator() + stackTrace
            logger.error(message)
            logger.error(t)
            reportError(new Error(message, t), Namespace("localErrorTable"))
          }
          case Success(localCount) if localCount > configuration.localErrorThreshold => {
            logger.error("reached local error threshold for " + namespace.toString + " [" + localCount + "]")
            logger.error(t)
            stop()
            terminate()
          }
          case Success(localCount) => {
            logger.warn(namespace.toString + " failed " + localCount + " times locally " +
              " and " + count + " globally")
            logger.debug(t)
            localErrorTable.reportError(namespace, System.currentTimeMillis(), instanceId, t.toString, sb.toString())
            errorTable.reportError(namespace, System.currentTimeMillis(), instanceId, t.toString, sb.toString())
            Thread.sleep(configuration.environmentRepeatConfiguration.timeout(count))
          }
        }
      }
    }
  }

}

