package ohnosequences.compota.local

import java.io.File
import java.net.URL

import ohnosequences.compota.Namespace
import ohnosequences.compota.console.{AnyWorkerInfo, Console}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.logging.Logger

import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}

import scala.collection.JavaConversions._


class LocalConsole[U](localCompota: LocalCompota[U], env: LocalEnvironment,  nisperoGraph: NisperoGraph) extends
  Console[LocalEnvironment, U, AnyLocalNispero, LocalCompota[U]](localCompota, env, nisperoGraph) {

  override def getNamespaceLog(id: String): Try[Either[URL, String]] = {
    Try {
      val log = scala.io.Source.fromFile(localCompota.localConfiguration.taskLogFile(new Namespace(id))).getLines().mkString
      Right(log)
    }
  }

  override def sidebar: NodeSeq = {
    <ul class="nav nav-sidebar">
      {nisperosLinks()}
    </ul>
      <ul class="nav nav-sidebar">
        <li><a href="/errors">errors</a></li>
        <li><a href="/threads">threads</a></li>
        <li><a href="#" class="undeploy">undeploy</a></li>
      </ul>
  }

  override def shutdown(): Unit = {
    logger.info("shutdown")
  }

  override def terminateInstance(id: String): Try[Unit] = {
    localCompota.terminateInstance(InstanceId(id))
  }

  override def getInstanceLog(instanceId: String): Try[Either[URL, String]] = {
    localCompota.getInstanceLog(InstanceId(instanceId))
  }


  case class ListWorkerInfoLocal(env: LocalEnvironment) extends AnyWorkerInfo {

    override def instanceId: InstanceId = env.instanceId

    override def printState: String = {
      env.getThreadInfo match {
        case Some((t, st)) => t.getState.toString
        case None => "n/a"
      }
    }
    override def printConnectAction: NodeSeq = xml.NodeSeq.Empty
  }


  override type ListWorkerInfo = ListWorkerInfoLocal

  override def listWorkers(nispero: String, lastWorkerToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ListWorkerInfo])] = {
    val workerInfo: List[ListWorkerInfoLocal] = localCompota.instancesEnvironments.filter { case (id, (nisp, e)) =>
      nisp.configuration.name.equals(nispero)
    }.map { case (id, (nisp, e)) =>
      ListWorkerInfoLocal(e)
    }.toList

    lastWorkerToken match {
      case None => listWorkers(workerInfo, limit)
      case Some(l) => listWorkers(workerInfo.drop(l.toInt + 1), limit)
    }
  }

  def listWorkers(workerInfo: List[ListWorkerInfo], limit: Option[Int]): Try[(Option[String], List[ListWorkerInfo])] = {
    val (lastWorker, workerInfoList) = limit match {
      case None => {
        (None, workerInfo)
      }
      case Some(l) if l < workerInfo.size - 1 => {
        (Some(l.toString), workerInfo.take(l))
      }
      case Some(l) => {
        (Some(l.toString), workerInfo)
      }
    }
    Success((lastWorker, workerInfoList))
  }


  override def nisperoInfoDetails(nispero: AnyLocalNispero): NodeSeq = {
    <table class="table table-striped topMargin20">
      <tbody>
        <tr>
          <td class="col-md-6">workers amount</td>
          <td class="col-md-6">
            {nispero.localConfiguration.workers}
          </td>
        </tr>
        <tr>
          <td class="col-md-6">working directory</td>
          <td class="col-md-6">
            {nispero.localConfiguration.workingDirectory.getAbsolutePath}
          </td>
        </tr>
      </tbody>
    </table>
  }


  override def sshInstance(id: String): Try[String] = {
    Failure(new Error("ssh is not supported by local nispero"))
  }

  override def compotaInfoPageDetails: NodeSeq = {
    <table class="table table-striped topMargin20">
      <tbody>
        <tr>
          <td class="col-md-6">working directory</td>
          <td class="col-md-6">
            {localCompota.localConfiguration.workingDirectory.getAbsolutePath}
          </td>
        </tr>
        <tr>
          <td class="col-md-6">error threshold</td>
          <td class="col-md-6">
            {localCompota.localConfiguration.errorThreshold}
          </td>
        </tr>
      </tbody>
    </table>
  }

  override def queueStatus(name: String): NodeSeq = {
    nisperoGraph.queues.get(name) match {
      case None => {
        errorDiv("queue " + name + " doesn't exist")
      }
      case Some(queueOp: LocalQueueOp[_])  => {
        <table class="table table-striped topMargin20">
          <tbody>
            <tr>
              <td class="col-md-6">messages</td>
              <td class="col-md-6">
                {queueOp.queue.rawQueue.size()}
              </td>
            </tr>
          </tbody>
        </table>
      }
    }
  }

  override def printInstanceStackTrace(instanceId: String): Try[String] = {
    Option(localCompota.instancesEnvironments.get(InstanceId(instanceId))) match {
      case None => Failure(new Error("instance " + instanceId + " does not exist"))
      case Some((n, e)) => {
        e.getThreadInfo match {
          case None => Failure(new Error("couldn't get stack trace for " + instanceId))
          case Some((t, a)) => {
            val stackTrace = new StringBuffer()
            a.foreach { s =>
              stackTrace.append("    at " + s.toString)
            }
            Success(stackTrace.toString)
          }
        }
      }
    }
  }

  override def mainHTML: String = {
    scala.io.Source.fromFile(new File("E:\\reps\\compota\\src\\main\\resources\\console\\main.html")).getLines().mkString(System.lineSeparator())
  }
}
