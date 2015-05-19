package ohnosequences.compota.console

import java.net.URL

import ohnosequences.awstools.utils.SQSUtils
import ohnosequences.compota.aws.queues.DynamoDBQueueOP
import ohnosequences.compota.graphs.NisperoGraph
import ohnosequences.compota._
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.compota.local.LocalQueueOp
import ohnosequences.compota.queues.{AnyQueueOp, AnyQueue}
import ohnosequences.logging.Logger

import scala.sys.process._
import scala.util.{Success, Failure, Try}
import scala.xml.{Node, NodeSeq}

abstract class AnyConsole {

  val logger: Logger

  def password: String

  def compotaInfoPage: NodeSeq

  def sidebar: NodeSeq



  def errorsPage: NodeSeq

  def nisperoInfoPage(nisperoName: String): NodeSeq

  def printErrorTable(lastToken: Option[String]): NodeSeq

  def printErrorStackTrace(namespace: String, timestamp: String, instanceId: String): Try[String]

  def printErrorMessage(namespace: String, timestamp: String, instanceId: String): Try[String]

  def sendUndeployCommand(reason: String, force: Boolean): Unit

  def printWorkers(nispero: String, lastToken: Option[String]): NodeSeq

  def printMessages(queueName: String, lastToken: Option[String]): NodeSeq

  def mainHTML: String

  def mainCSS: String

  def name: String

  def shutdown(): Unit

  def getInstanceLogRaw(instanceId: String): Try[Either[URL, String]]

  def printInstanceLog(instanceId: String): NodeSeq

  def printInstanceStackTrace(instanceId: String): NodeSeq

  def terminateInstance(id: String): NodeSeq

  def sshInstance(id: String): NodeSeq

  def getNamespaceLog(id: String): Try[Either[URL, String]]

  def getMessage(queue: String, id: String): Try[Either[URL, String]]

}

trait AnyWorkerInfo {

  def instanceId: InstanceId

  def printState: String = ""

  def printConnectAction: NodeSeq = {
    <a class="btn btn-info sshInstance" href="#" data-id={instanceId.id}>
      <i class="icon-refresh icon-white"></i>
      SSH</a>
  }

  def printTerminateAction: NodeSeq = {
    <a class="btn btn-danger terminateInstance" href="#" data-id={instanceId.id}>
      <i class="icon-refresh icon-white"></i>
      Terminate</a>
  }

  def printStackTrace: NodeSeq = {
    <a class="btn btn-info instanceStackTrace" href="#" data-id={instanceId.id}>
      <i class="icon-refresh icon-white"></i>
      Stack trace</a>
  }

  def viewLog: NodeSeq = {
    <a class="btn btn-info viewInstanceLog" href="#" data-id={instanceId.id}>
      <i class="icon-refresh icon-white"></i>
      View Log</a>
  }

  def printInstance: Node = {
    <p>{instanceId.id}</p>
  }

}




abstract class Console[E <: AnyEnvironment, U, N <: AnyNispero.of[E], C <: Compota[E, N, U]](compota: C, env: E, nisperoGraph: NisperoGraph) extends AnyConsole{

  override def password: String = compota.configuration.consolePassword

  override val logger: Logger = env.logger.fork("console")

  def compotaInfoPage: NodeSeq = {
    compotaInfoPageHeader ++ compotaInfoPageDetails
  }



  def getInstanceLog(instanceId: InstanceId): Try[String]

  override def printInstanceLog(instanceId: String): NodeSeq = {
    preResult(getInstanceLog(InstanceId(instanceId)))
  }


  def getSSHInstance(id: InstanceId): Try[String]

  override def sshInstance(id: String): NodeSeq = {
    preResult(getSSHInstance(InstanceId(id)))
  }


  def getTerminateInstance(id: InstanceId): Try[String]

  override def terminateInstance(id: String): NodeSeq = {
    preResult(getTerminateInstance(InstanceId(id)))
  }


  def getInstanceStackTrace(id: InstanceId): Try[String]

  override def printInstanceStackTrace(instanceId: String): NodeSeq = {
    preResult(getInstanceStackTrace(InstanceId(instanceId)))
  }


  def compotaInfoPageHeader: NodeSeq = {
    <div class="page-header">
      <h1>
        {compota.configuration.name}
      </h1>
    </div>
  }

  def queueStatus(queueOp: AnyQueueOp): NodeSeq = {
    <h3>{queueOp.queue.name}</h3>
    <table class="table table-striped topMargin20">
      <tbody>
        {queueOp match {
          case localQueueOp: LocalQueueOp[_] => {
            <tr>
              <td class="col-md-6">queue size</td>
              <td class="col-md-6">
                {localQueueOp.queue.rawQueue.size()}
              </td>
            </tr>
              <tr>
                <td class="col-md-6">persistant queue size</td>
                <td class="col-md-6">
                  {localQueueOp.queue.rawQueueP.size()}
                </td>
              </tr>
          }
          case dynamoQueueOp: DynamoDBQueueOP[_] => {
            dynamoQueueOp.sqsInfo() match {
              case Failure(t) =>  xml.NodeSeq.Empty
              case Success(sqsInfo) => {
                <tr>
                  <td class="col-md-6">SQS queue messages (approx)</td>
                  <td class="col-md-6">
                    {sqsInfo.approx}
                  </td>
                </tr>
                <tr>
                    <td class="col-md-6">SQS messages in flight (approx)</td>
                    <td class="col-md-6">
                      {sqsInfo.inFlight}
                    </td>
                </tr>
              }
            }
          }
        }}
      </tbody>
    </table>
    <a class="btn btn-info showQueueMessages" href="#" data-queue={queueOp.queue.name}>
      <i class="icon-refresh icon-white"></i>
      Show messages
    </a>
  }


  def compotaInfoPageDetails: NodeSeq

  def sidebar: NodeSeq = {
    <ul class="nav nav-sidebar">
      {nisperosLinks()}
    </ul>
      <ul class="nav nav-sidebar">
        <li><a href="/errors">errors</a></li>
        <li><a href="#" class="undeploy">undeploy</a></li>
      </ul>
  }

  def nisperosLinks(): NodeSeq = {
    val l = for {(name, nispero) <- compota.nisperosNames}
    yield <li>
        <a href={"/nispero/" + name}>
          {name}
        </a>
      </li>
    l.toList
  }

  def errorsPage: NodeSeq = {
    <h2>Errors</h2>
      <table class="table table-striped">
        <thead>
          <tr>
            <th class="col-md-3">id</th>
            <th class="col-md-3">instance</th>
            <th class="col-md-3">time</th>
            <th class="col-md-3">message</th>
          </tr>
        </thead>
        <tbody id ="errorsTableBody">
          {printErrorTable(None)}
        </tbody>
      </table>
      <p><a class="btn btn-info loadMoreErrors" href="#">
        <i class="icon-refresh icon-white"></i>
        Show more
      </a></p>
  }

  def errorDiv(message: String): NodeSeq = {
    <div class="alert alert-danger">
      {message}
    </div>
  }

  def preResult(res: Try[String]): NodeSeq = {
    res match {
      case Failure(t) => {
        <pre class="alert alert-danger pre-scrollable">{t.toString}</pre>
      }
      case Success(s) => {
        <pre class="alert alert-success pre-scrollable">{s}</pre>
      }
    }
  }

  def nisperoInfoPage(nisperoName: String): NodeSeq = {
    compota.nisperosNames.get(nisperoName) match {
      case None => {
        errorDiv(nisperoName + " doesn't exist")
      }
      case Some(nispero) =>
        <div class="page-header">
          <h1>
            {compota.configuration.name}
            <small>{nisperoName} nispero</small>
          </h1>
        </div>
          <div>
            {nisperoInfoDetails(nispero)}
          </div>

          <div class="page-header">
            <h2>input</h2>
          </div>
          <div> {
            nisperoGraph.queueOps(nispero.inputQueue).map(queueStatus)
            } </div>
          <div class="page-header">
            <h2>output</h2>
          </div>
          <div> {nisperoGraph.queueOps(nispero.outputQueue).map(queueStatus)} </div>
          <div class="page-header">
            <h2>instances</h2>
          </div>
          <table class="table table-striped topMargin20">
            <tbody id="workerInstances">
              {printWorkers(nisperoName, None)}
            </tbody>
          </table>
    }
  }

  def nisperoInfoDetails(nispero: N): NodeSeq

  def printErrorTable(lastToken: Option[String]): NodeSeq = {
    listErrorTable(lastToken, Some(10)) match {
      case Failure(t) => printErrorTableError(lastToken, t)
      case Success((lToken, errors)) => {
        errors.map { item =>
          printErrorTableItem(lToken, item)
        }
      }
    }
  }

  def listErrorTable(lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ErrorTableItem])] = {
    env.errorTable.listErrors(lastToken, limit)
  }

  def printErrorTableError(lastToken: Option[String], t: Throwable): NodeSeq = {
    errorTr("error: " + t.toString, 3, lastToken)
  }

  def printErrorTableItem(lastToken: Option[String], item: ErrorTableItem): Node = {
    <tr data-lastToken={lastToken.getOrElse("")}>
      <td>
        <a href={"/logging/namespace/" + item.namespace.toString}>{item.namespace.toString}</a>
      </td>
      <td>
        <a href={"/instance/" + item.instanceId.id + "/log"}>{item.instanceId.id}</a>
      </td>
      <td>
        {item.formattedTime()}
      </td>
      <td>
        <a href={"/error/message/" + item.namespace.toString + "/" + item.timestamp + "/" + item.instanceId.id}>message</a>
      </td>
      <td>
        <a href={"/error/stackTrace/" + item.namespace.toString + "/" + item.timestamp + "/" + item.instanceId.id}>message</a>
      </td>
    </tr>
  }

  def printErrorStackTrace(namespace: String, timestamp: String, instanceId: String): Try[String] = {
    env.errorTable.getError(Namespace(namespace), timestamp.toLong, InstanceId(instanceId)).map(_.stackTrace)
  }

  def printErrorMessage(namespace: String, timestamp: String, instanceId: String): Try[String] = {
    env.errorTable.getError(Namespace(namespace), timestamp.toLong, InstanceId(instanceId)).map(_.message)
  }



  def listMessages(queueName: String, lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[String])] = {
    nisperoGraph.queueOpNames.get(queueName) match {
      case None => Failure(new Error("queue " + queueName + " not found"))
      case Some(queueOp) => {
        queueOp.list(lastToken, limit)
      }
    }
  }

  def printMessages(queueName: String, lastToken: Option[String]): NodeSeq = {
    listMessages(queueName, lastToken, Some(10)) match {
      case Failure(t) => {
        printListMessagesError(t, lastToken)
      }
      case Success((newLastToken, list)) => {
        list.map { id =>
          printMessage(queueName, id, newLastToken)
        }
      }
    }
  }

  def printListMessagesError(t: Throwable, lastToken: Option[String]): NodeSeq = {
    errorTr("error: " + t.toString, 3, lastToken)
  }

  def printMessage(queueName: String, id: String, lastToken: Option[String]): Node = {
    <tr data-lastToken={lastToken.getOrElse("")}>
      <td class="col-md-4">
        {id}
      </td>
      <td class="col-md-4">
        <a href={"/queue/" + queueName + "/message/" + id}>download</a>
      </td>
      <td class="col-md-4">
        <a href={"/logging/namespace/" + id}>log</a>
      </td>
    </tr>
  }


  def getMessage(queue: String, id: String): Try[Either[URL, String]] = {
    nisperoGraph.queueOpNames.get(queue) match {
      case None => Failure(new Error("queue " + queue + " doesn't exists"))
      case Some(queueOp) =>  queueOp.getContent(id)
    }
  }


  type ListWorkerInfo <: AnyWorkerInfo

  def listWorkers(nispero: String, lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ListWorkerInfo])]

  def printWorkers(nispero: String, lastToken: Option[String]): NodeSeq = {
    listWorkers(nispero, lastToken, Some(10)) match {
      case Failure(t) => printWorkerError(t, lastToken)

      case Success((newLastToken, list)) => {
        list.map(printWorker(_, newLastToken))
      }
    }
  }

  def errorTr(message: String, cols: Int, lastToken: Option[String]): NodeSeq = {
    <tr class="danger" data-lastToken={lastToken.getOrElse("")}><td colspan={cols.toString}>{message}</td></tr>
  }


  def printWorkerError(t: Throwable, lastToken: Option[String]): NodeSeq = {
    errorTr("error: " + t.toString, 3, lastToken)
  }

  def printWorker(workerInfo: ListWorkerInfo, lastToken: Option[String]): Node = {
    <tr data-lastToken={lastToken.getOrElse("")}>
      <td class="col-md-4">
        {workerInfo.printInstance}
      </td>
      <td class="col-md-4">
        {workerInfo.printState}
      </td>
      <td class="col-md-4">
        {workerInfo.printConnectAction ++ workerInfo.printTerminateAction ++ workerInfo.printStackTrace ++ workerInfo.viewLog}
      </td>
    </tr>
  }

  def name: String = compota.configuration.name

  def sendUndeployCommand(reason: String, force: Boolean): Unit = {
    compota.sendUnDeployCommand(env, reason, force)
  }

  def mainCSS: String = {
    val css = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/console/main.css")).mkString
    css
  }

  def mainHTML: String = {
    val main = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/console/main.html")).mkString
    main
  }

}
