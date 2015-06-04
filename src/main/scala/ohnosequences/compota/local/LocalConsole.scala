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


class LocalConsole[N <: AnyLocalNispero](localCompota: AnyLocalCompota.of2[N], env: LocalEnvironment,  nisperoGraph: NisperoGraph) extends
  Console[LocalEnvironment, N, AnyLocalCompota.of2[N]](localCompota, env, nisperoGraph) {

  override def getNamespaceLog(id: String): Try[Either[URL, String]] = {
    Try {
      val log = scala.io.Source.fromFile(localCompota.configuration.taskLogFile(new Namespace(id))).getLines().mkString
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

  override def getTerminateInstance(id: InstanceId): Try[String] = {
    localCompota.terminateInstance(id).map(_ => "instance " + id.id  + " terminated")
  }

  override def getInstanceLogRaw(instanceId: String): Try[Either[URL, String]] = {
    localCompota.getInstanceLog(InstanceId(instanceId)).map(Right(_))
  }


  override def getInstanceLog(instanceId: InstanceId): Try[String] = {
    localCompota.getInstanceLog(instanceId)
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


  override def nisperoInfoDetails(nispero: N): NodeSeq = {
    <table class="table table-striped topMargin20">
      <tbody>
        <tr>
          <td class="col-md-6">workers amount</td>
          <td class="col-md-6">
            {nispero.configuration.workers}
          </td>
        </tr>
        <tr>
          <td class="col-md-6">working directory</td>
          <td class="col-md-6">
            {nispero.configuration.workingDirectory.getAbsolutePath}
          </td>
        </tr>
      </tbody>
    </table>
  }


  override def getSSHInstance(id: InstanceId): Try[String] = {
    Failure(new Error("ssh is not supported by local nispero"))
  }

  override def compotaInfoPageDetails: NodeSeq = {
    <table class="table table-striped topMargin20">
      <tbody>
        <tr>
          <td class="col-md-6">working directory</td>
          <td class="col-md-6">
            {localCompota.configuration.workingDirectory.getAbsolutePath}
          </td>
        </tr>
        <tr>
          <td class="col-md-6">error threshold</td>
          <td class="col-md-6">
            {localCompota.configuration.errorThreshold}
          </td>
        </tr>
      </tbody>
    </table>
  }


  override def getInstanceStackTrace(instanceId: InstanceId): Try[String] = {
    Option(localCompota.instancesEnvironments.get(instanceId)) match {
      case None => Failure(new Error("instance " + instanceId + " does not exist"))
      case Some((n, e)) => {
        e.getThreadInfo match {
          case None => Failure(new Error("couldn't get stack trace for " + instanceId))
          case Some((t, a)) => {
            val stackTrace = new StringBuffer()
            a.foreach { s =>
              stackTrace.append("at " + s.toString + System.lineSeparator())
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
