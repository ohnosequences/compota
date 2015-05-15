package ohnosequences.compota.local

import java.net.URL

import ohnosequences.compota.Namespace
import ohnosequences.compota.console.{AnyWorkerInfo, Console}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.logging.Logger

import scala.util.{Success, Try}
import scala.xml.{Node, NodeSeq}

import scala.collection.JavaConversions._


class LocalConsole[U](localCompota: LocalCompota[U], env: LocalEnvironment,  nisperoGraph: NisperoGraph) extends Console(localCompota, env) {

  override def getNamespaceLog(id: String): Try[Either[URL, String]] = {
    Try {
      val log = io.Source.fromFile(localCompota.localConfiguration.taskLogFile(new Namespace(id))).getLines().mkString
      Right(log)
    }
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
      nisp.name.equals(nispero)
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


  override def listErrors(last: Option[(String, String)]): NodeSeq = ???

  override def sshInstance(id: String): Try[Unit] = ???

  override def compotaInfoPageDetails: NodeSeq = ???

  override def mainHTML: String = ???

  override def queueStatus(name: String): NodeSeq = ???

}
