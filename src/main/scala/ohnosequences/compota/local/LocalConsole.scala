package ohnosequences.compota.local

import java.io.File
import java.net.URL

import ohnosequences.compota.Namespace
import ohnosequences.compota.console.{AnyInstanceInfo, Pagination, Console}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.{QueueChecker}
import ohnosequences.compota.queues.AnyQueueOp

import scala.util.{Failure, Success, Try}
import scala.xml.{NodeSeq}

import scala.collection.JavaConversions._


class LocalConsole[N <: AnyLocalNispero](localCompota: AnyLocalCompota.of2[N],
                                         env: LocalEnvironment,
                                         controlQueueOp: AnyQueueOp,
                                         nisperoGraph: QueueChecker[LocalEnvironment]) extends
  Console[LocalEnvironment, N, AnyLocalCompota.of2[N]](localCompota, env, controlQueueOp, nisperoGraph) {


  import ohnosequences.compota.console.GeneralComponents._

  override def getLogRaw(instanceId: String, namespace: Seq[String]): Try[Either[URL, String]] = {
    localCompota.getLog(env, InstanceId(instanceId), Namespace(namespace)).map { s =>
      Right(s)
    }
  }

  override def printLog(instanceId: String, namespace: Seq[String]): NodeSeq = {
    preResult(localCompota.getLog(env, InstanceId(instanceId), Namespace(namespace)))
  }

  override def sidebar: NodeSeq = {
    <ul class="nav nav-sidebar">
      <li><a href="/"><strong>home</strong></a></li>
    </ul>
    <ul class="nav nav-sidebar">
      {nisperosLinks}
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


  override def terminateInstance(instanceId: String, namespace: Seq[String]): NodeSeq = {
    preResult(localCompota.terminateInstance(env, InstanceId(instanceId), Namespace(namespace)).map(_ => "instance " + instanceId  + " terminated"))
  }

  case class LocalEnvironmentInfo(env: LocalEnvironment) extends AnyInstanceInfo {

    override def instanceId: InstanceId = env.instanceId

    override def namespace: Namespace = env.namespace

    override def printState: NodeSeq = {
      env.getThreadInfo match {
        case Some((t, st)) => <p>{t.getState.toString}</p>
        case None => <p>n/a</p>
      }
    }
    override def printConnectAction: NodeSeq = xml.NodeSeq.Empty
  }

  override type InstanceInfo = LocalEnvironmentInfo

  override def listWorkers(nispero: N, lastWorkerToken: Option[String], limit: Option[Int]): Try[(Option[String], List[LocalEnvironmentInfo])] = {
    localCompota.listNisperoWorkers(env, nispero).map { list =>
      // env.logger.info("get workers info: " + list)
      val workerInfo: List[LocalEnvironmentInfo] = list.map(LocalEnvironmentInfo)
      Pagination.listPagination(workerInfo, limit, lastWorkerToken)
    }
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
          <td class="col-md-6">global error threshold</td>
          <td class="col-md-6">
            {localCompota.configuration.globalErrorThreshold}
          </td>
        </tr>
      </tbody>
    </table>

  }

  override def namespacePage: NodeSeq = {
    <h2>Instances and namespaces</h2>
      <table class="table table-striped">
        <thead>
          <tr>
            <th class="col-md-3">instance/namespace</th>
            <th class="col-md-3">status</th>
            <th class="col-md-3">actions</th>
          </tr>
        </thead>
        <table class="table table-striped topMargin20">
          <tbody id="namespacesTableBody">
            {printNamespaces(None)}
          </tbody>
        </table>
      </table>
      <p><a class="btn btn-info loadMoreNamespaces" href="#">
        <i class="icon-refresh icon-white"></i>
        Show more
      </a></p>
  }

  def listNamespaces(lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[LocalEnvironmentInfo])] = {
    Try {
      val namespaces: List[LocalEnvironmentInfo] = env.environments.map { case ((instance, namespace), e) =>
        new LocalEnvironmentInfo(e)
      }.toList
      Pagination.listPagination(namespaces, limit, lastToken)
    }
  }


  override def listManagers(lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[LocalEnvironmentInfo])] = {
    Try {
      val managers: List[LocalEnvironmentInfo] = env.environments.filter { case ((instance, namespace), e) =>
        namespace.getPath.toLowerCase.contains("manager")
      }.map { case ((instance, namespace), e) =>
        new LocalEnvironmentInfo(e)
      }.toList
      Pagination.listPagination(managers, limit, lastToken)
    }
  }

  def printNamespaces(lastToken: Option[String]): NodeSeq = {
    val page = listNamespaces(lastToken, Some(localCompota.configuration.consoleNamespacesPageSize))
    printInstanceInfoPage(page, lastToken)
  }

  override def stackTraceInstance(instanceId: String, namespace: Seq[String]): NodeSeq = {
    preResult(localCompota.getStackTrace(env, InstanceId(instanceId), Namespace(namespace)))
  }

  override def sshInstance(instanceId: String, namespace: Seq[String]): NodeSeq = xml.NodeSeq.Empty

//  override def mainHTML: String = {
//    scala.io.Source.fromFile(new File("E:\\reps\\compota\\src\\main\\resources\\console\\main.html")).getLines().mkString(System.lineSeparator())
//  }
}
