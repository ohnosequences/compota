package ohnosequences.compota.console

import java.net.URL

import ohnosequences.awstools.utils.SQSUtils
import ohnosequences.compota.aws.queues.DynamoDBQueueOP
import ohnosequences.compota.graphs.{QueueChecker, NisperoGraph}
import ohnosequences.compota._
import ohnosequences.compota.environment.{AnyEnvironment, InstanceId}
import ohnosequences.compota.local.{LocalEnvironment, LocalQueueOp}
import ohnosequences.compota.queues.{AnyQueueOp, AnyQueue}
import ohnosequences.logging.Logger

import scala.sys.process._
import scala.util.{Success, Failure, Try}
import scala.xml.{Node, NodeSeq}

object GeneralComponents {
  def errorDiv(logger: Logger, message: String): NodeSeq = {
    logger.error(message)
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

  def errorTr(logger: Logger, message: String, cols: Int, lastToken: Option[String]): NodeSeq = {
    logger.error(message)
    <tr class="danger" data-lasttoken={lastToken.getOrElse("")}><td colspan={cols.toString}>{message}</td></tr>
  }
}

abstract class AnyConsole {

  val logger: Logger

  def password: String

  def compotaInfoPage: NodeSeq

  def sidebar: NodeSeq

  def isHTTPS = true

  def errorsPage: NodeSeq

  def namespacePage: NodeSeq

  def nisperoInfoPage(nisperoName: String): NodeSeq

  def printErrorTable(lastToken: Option[String]): NodeSeq

  def getErrorStackTrace(instanceId: String, namespace: Seq[String], timestamp: String): Try[String]

  def getErrorMessage(instanceId: String, namespace: Seq[String], timestamp: String): Try[String]

  def sendForceUnDeployCommand(reason: String, message: String): Unit

  def printWorkers(nispero: String, lastToken: Option[String]): NodeSeq

  def printManagers(lastToken: Option[String]): NodeSeq

  def printNamespaces(lastToken: Option[String]): NodeSeq

  def printMessages(queueName: String, lastToken: Option[String]): NodeSeq

  def mainHTML: String

  def mainCSS: String

  def name: String

  def shutdown(): Unit

  def getLogRaw(instanceId: String, namespace: Seq[String]): Try[Either[URL, String]]

  def printLog(instanceId: String, namespace: Seq[String]): NodeSeq

  def terminateInstance(instanceId: String, namespace: Seq[String]): NodeSeq

  def sshInstance(instanceId: String, namespace: Seq[String]): NodeSeq

  def stackTraceInstance(instanceId: String, namespace: Seq[String]): NodeSeq

  def getMessage(queue: String, id: String): Try[Either[URL, String]]

}

//todo split model from view
trait AnyInstanceInfo {

  def instanceId: InstanceId

  def namespace: Namespace

  def printState: NodeSeq

  def printInstanceInfoError(logger: Logger, t: Throwable, lastToken: Option[String]): NodeSeq = {
    GeneralComponents.errorTr(logger, t.toString, 3, lastToken)
  }

  def printInstanceInfo(lastToken: Option[String]): Node = {
    <tr data-lasttoken={lastToken.getOrElse("")}>
      <td class="col-md-4">
        {printInstance}
      </td>
      <td class="col-md-4">
        {printState}
      </td>
      <td class="col-md-4">
        {printConnectAction ++ printTerminateAction ++ printStackTrace ++ viewLog}
      </td>
    </tr>
  }



  def printConnectAction: NodeSeq = {
    <a class="btn btn-info sshInstance" href="#" data-instance={instanceId.id} data-namespace={namespace.getPath}>
      <i class="icon-refresh icon-white"></i>
      SSH</a>
  }

  def printTerminateAction: NodeSeq = {
    <a class="btn btn-danger terminateInstance" href="#" data-instance={instanceId.id} data-namespace={namespace.getPath}>
      <i class="icon-refresh icon-white"></i>
      Terminate</a>
  }

  def printStackTrace: NodeSeq = {
    <a class="btn btn-info instanceStackTrace" href="#" data-instance={instanceId.id} data-namespace={namespace.getPath}>
      <i class="icon-refresh icon-white"></i>
      Stack trace</a>
  }

  def viewLog: NodeSeq = {
    <a class="btn btn-info viewLog" href="#" data-instance={instanceId.id} data-namespace={namespace.getPath}>
      <i class="icon-refresh icon-white"></i>
      View Log</a>
  }

  def printInstance: Node = {
    <p>{instanceId.id + (if (namespace.toString.isEmpty) "" else "/" + namespace.getPath)}</p>
  }

}

object AnyInstanceInfo {
  def printInstanceInfoError(logger: Logger, t: Throwable, lastToken: Option[String]): NodeSeq = {
    GeneralComponents.errorTr(logger, t.toString, 3, lastToken)
  }
}



abstract class Console[E <: AnyEnvironment[E], N <: AnyNispero.of[E], C <: AnyCompota.of[E, N]](
                                                                                                 compota: C,
                                                                                                 env: E,
                                                                                                 controlQueueOp: AnyQueueOp,
                                                                                                 nisperoGraph: QueueChecker[E]) extends AnyConsole {


  import GeneralComponents._

  override val logger: Logger = env.logger

  override def password: String = compota.configuration.consolePassword

  override def isHTTPS: Boolean = compota.configuration.consoleHTTPS

  def compotaInfoPage: NodeSeq = {
    compotaInfoPageHeader ++ compotaInfoPageDetailsTable ++ compotaControlQueueDetails
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
          case _: AnyQueueOp => {
            xml.NodeSeq.Empty
          }
      }}
      </tbody>
    </table>
    <a class="btn btn-info showQueueMessages" href="#" data-queue={queueOp.queue.name}>
      <i class="icon-refresh icon-white"></i>
      Show messages
    </a>
  }


  def compotaInfoPageDetailsTable: NodeSeq

  def compotaControlQueueDetails: NodeSeq = {
    <h2>Control queue</h2>
    queueStatus(controlQueueOp)
  }


  def nisperosLinks: NodeSeq = {
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
            <th class="col-md-3">namespace</th>
            <th class="col-md-2">instance</th>
            <th class="col-md-2">time</th>
            <th class="col-md-3">message</th>
            <th class="col-md-2">stack trace</th>
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





  def nisperoInfoPage(nisperoName: String): NodeSeq = {
    compota.nisperosNames.get(nisperoName) match {
      case None => {
        errorDiv(logger, nisperoName + " doesn't exist")
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
          <div>
            {nispero.inputQueue.subQueues.map { queue =>
            nisperoGraph.queueOps.get(queue.name) match {
              case None => xml.NodeSeq.Empty
              case Some(queueOp) => queueStatus(queueOp)
            }
            }}
          </div>
          <div class="page-header">
            <h2>output</h2>
          </div>
          <div>
            {nispero.outputQueue.subQueues.map { queue =>
            nisperoGraph.queueOps.get(queue.name) match {
              case None => xml.NodeSeq.Empty
              case Some(queueOp) => queueStatus(queueOp)
            }
          }}
          </div>
          <div class="page-header">
            <h2>instances</h2>
          </div>
          <table class="table table-striped topMargin20">
            <tbody id="nisperoWorkersTableBody">
              {printWorkers(nisperoName, None)}
            </tbody>
          </table>
          <p><a class="btn btn-info loadMoreNisperoWorkers" href="#" data-nispero={nispero.configuration.name}>
            <i class="icon-refresh icon-white"></i>
            Show more
          </a></p>
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
    errorTr(logger, "error: " + t.toString, 3, lastToken)
  }

  def printErrorTableItem(lastToken: Option[String], item: ErrorTableItem): Node = {
    <tr data-lasttoken={lastToken.getOrElse("")}>
      <td>
        <a href={"/logging/" + item.instanceId.id + "/" + item.namespace.getPath}>{item.namespace.toString}</a>
      </td>
      <td>
        <a href={"/logging/" + item.instanceId.id}>{item.instanceId.id}</a>
      </td>
      <td>
        {item.formattedTime()}
      </td>
      <td>
        <a href={"/error/message/" + item.timestamp + "/" + item.instanceId.id + "/" + item.namespace.getPath}>message</a>
      </td>
      <td>
        <a href={"/error/stackTrace/" + item.timestamp + "/" + item.instanceId.id + "/" + item.namespace.getPath}>stack trace</a>
      </td>
    </tr>
  }

  override def getErrorStackTrace(instanceId: String, namespace: Seq[String], timestamp: String): Try[String] = {
    env.errorTable.getError(Namespace(namespace), timestamp.toLong, InstanceId(instanceId)).map(_.stackTrace)
  }

  override def getErrorMessage(instanceId: String, namespace: Seq[String], timestamp: String): Try[String] = {
    env.errorTable.getError(Namespace(namespace), timestamp.toLong, InstanceId(instanceId)).map(_.message)
  }



  def listMessages(queueName: String, lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[String])] = {

    if(queueName.equals(controlQueueOp.queue.name)) {
      controlQueueOp.list(lastToken, limit)
    } else {
      nisperoGraph.queueOps.get(queueName) match {
        case None => Failure(new Error("queue " + queueName + " not found"))

        case Some(queueOp) => {
          queueOp.list(lastToken, limit)
        }
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
    errorTr(logger, "error: " + t.toString, 3, lastToken)
  }

  def printMessage(queueName: String, id: String, lastToken: Option[String]): Node = {
    <tr data-lasttoken={lastToken.getOrElse("")}>
      <td class="col-md-4">
        {id}
      </td>
      <td class="col-md-4">
        <a href={"/queue/" + queueName + "/message/" + id}>download</a>
      </td>
      <td class="col-md-4">
        <a href={"/logging/raw/global/" + id}>log</a>
      </td>
    </tr>
  }


  def getMessage(queue: String, id: String): Try[Either[URL, String]] = {
    if(queue.equals(controlQueueOp.queue.name)) {
      controlQueueOp.getContent(id)
    } else {
      nisperoGraph.queueOps.get(queue) match {
        case None => Failure(new Error("queue " + queue + " doesn't exists"))
        case Some(queueOp) => queueOp.getContent(id)
      }
    }
  }


  type InstanceInfo <: AnyInstanceInfo

  def printInstanceInfoPage(page: Try[(Option[String], List[InstanceInfo])], lastToken: Option[String]): NodeSeq = page match {
    case Failure(t) => AnyInstanceInfo.printInstanceInfoError(logger, t, lastToken)
    case Success((newLastToken, list)) =>  list.map(_.printInstanceInfo(newLastToken))
  }

  def listWorkers(nispero: N, lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[InstanceInfo])]

  def listManagers(lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[InstanceInfo])]

  def printWorkers(nisperoName: String, lastToken: Option[String]): NodeSeq = {
    compota.nisperosNames.get(nisperoName) match {
      case Some(nispero) => {
        val page = listWorkers(nispero, lastToken, Some(compota.configuration.consoleInstancePageSize))
        printInstanceInfoPage(page, lastToken)
      }
      case None => {
        AnyInstanceInfo.printInstanceInfoError(logger, new Error("nispero " + nisperoName + " doesn't exist"), lastToken)
      }
    }
  }

  def printManagers(lastToken: Option[String]): NodeSeq = {
    val page = listManagers(lastToken, Some(compota.configuration.consoleInstancePageSize))
    printInstanceInfoPage(page, lastToken)
  }

  def name: String = compota.configuration.name

  def sendForceUnDeployCommand(reason: String, message: String): Unit = {
    compota.sendForceUnDeployCommand(env, reason, reason)
  }

  def mainCSS: String = {
    val css = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/ohnosequences/compota/console/main.css")).mkString
    css
  }

  def mainHTML: String = {
    val main = scala.io.Source.fromInputStream(getClass.getResourceAsStream("ohnosequences/compota/console/main.html")).mkString
    main
  }

}
