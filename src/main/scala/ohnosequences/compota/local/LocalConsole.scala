package ohnosequences.compota.local

import java.io.File
import java.net.URL

import ohnosequences.compota.Namespace
import ohnosequences.compota.console.{AnyWorkerInfo, Console}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.{QueueChecker, NisperoGraph}
import ohnosequences.compota.queues.AnyQueueOp
import ohnosequences.logging.Logger

import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}

import scala.collection.JavaConversions._


class LocalConsole[N <: AnyLocalNispero](localCompota: AnyLocalCompota.of2[N],
                                         env: LocalEnvironment,
                                         controlQueueOp: AnyQueueOp,
                                         nisperoGraph: QueueChecker[LocalEnvironment]) extends
  Console[LocalEnvironment, N, AnyLocalCompota.of2[N]](localCompota, env, controlQueueOp, nisperoGraph) {


  override def getLogRaw(instanceId: String, namespace: String): Try[Either[URL, String]] = {
    localCompota.getLog(env.logger, InstanceId(instanceId), Namespace(namespace)).map { s =>
      Right(s)
    }
  }

  override def printLog(instanceId: String, namespace: String): NodeSeq = {
    preResult(localCompota.getLog(env.logger, InstanceId(instanceId), Namespace(namespace)))
  }

  override def sidebar: NodeSeq = {
    <ul class="nav nav-sidebar">
      <li><a href="/"><strong>home</strong></a></li>
    </ul>
    <ul class="nav nav-sidebar">
      {nisperosLinks()}
    </ul>
      <ul class="nav nav-sidebar">
        <li><a href="/errorsPage">errors</a></li>
        <li><a href="/namespacePage">namespaces</a></li>
        <li><a href="/threads">threads</a></li>
        <li><a href="#" class="undeploy">undeploy</a></li>
      </ul>
  }

  override def shutdown(): Unit = {
    logger.info("shutdown")
  }


  override def terminateInstance(instanceId: String): NodeSeq = {
    preResult(localCompota.terminateInstance(InstanceId(instanceId)).map(_ => "instance " + instanceId  + " terminated"))
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

  override def listWorkers(nisperoName: String, lastWorkerToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ListWorkerInfo])] = {
    localCompota.nisperosNames.get(nisperoName) match {
      case Some(nispero) => {
        localCompota.listNisperoWorkers(nispero).flatMap { list =>
          val workerInfo: List[ListWorkerInfoLocal] = list.map(ListWorkerInfoLocal)
          lastWorkerToken match {
            case None => listWorkers(workerInfo, limit)
            case Some(l) => listWorkers(workerInfo.drop(l.toInt + 1), limit)
          }
        }
      }
      case None => Failure(new Error("couldn't find nispero " + nisperoName))
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

  override def compotaInfoPageDetailsTable: NodeSeq = {
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


  def printNamespaceTable(): NodeSeq = {
   // logger.info(localCompota.environments.toString)

    localCompota.environments.toList.map { case ((inst, ns), env) =>
      printNamespaceItem(env)
    }
  }

  override def stackTraceInstance(instanceId: String, namespace: String): NodeSeq = {
    preResult(localCompota.getStackTrace(InstanceId(instanceId), Namespace(namespace)))
  }

  override def sshInstance(instanceId: String): NodeSeq = xml.NodeSeq.Empty

  override def mainHTML: String = {
    scala.io.Source.fromFile(new File("E:\\reps\\compota\\src\\main\\resources\\console\\main.html")).getLines().mkString(System.lineSeparator())
  }
}
