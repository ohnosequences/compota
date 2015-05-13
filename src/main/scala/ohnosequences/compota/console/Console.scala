package ohnosequences.compota.console

import java.net.URL

import ohnosequences.compota.AnyCompota
import ohnosequences.logging.Logger

import scala.sys.process._
import scala.util.Try
import scala.xml.NodeSeq

abstract class Console(compota: AnyCompota) {

  def password: String

  def sshConfigTemplate: String = {
    scala.io.Source.fromInputStream(getClass.getResourceAsStream("/console/keytoolConf.txt")).mkString
  }

  val logger: Logger

  def compotaInfoPage: NodeSeq = {
    compotaInfoPageHeader ++ compotaInfoPageDetails
  }

  def compotaInfoPageHeader: NodeSeq = {
    <div class="page-header">
      <h1>
        {compota.name}
      </h1>
    </div>
  }

  def queueStatus(name: String): NodeSeq

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
    val l = for {(name, nispero) <- compota.nisperos}
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
          {listErrors(None)}
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
        <div class="alert alert-danger">
          {nisperoName + " doesn't exist"}
        </div>
      }
      case Some(nispero) =>
        <div class="page-header">
          <h1>
            {compota.name}
            <small>{nisperoName} nispero</small>
          </h1>
        </div>
          <div>
            {nisperoInfoDetails(nisperoName)}
          </div>

          <div class="page-header">
            <h2>input</h2>
          </div>
          <div> {queueStatus(nispero.inputQueue.name)} </div>
          <div class="page-header">
            <h2>output</h2>
          </div>
          <div> {queueStatus(nispero.outputQueue.name)} </div>
          <div class="page-header">
            <h2>instances</h2>
          </div>
          <table class="table table-striped topMargin20">
            <tbody id="workerInstances">
              {workersInfo(nisperoName)}
            </tbody>
          </table>
    }
  }

  def nisperoInfoDetails(nisperoName: String): NodeSeq

  def listErrors(last: Option[(String, String)]): NodeSeq

  def listMessages(queueName: String, last: Option[String]): NodeSeq

  def shutdown(): Unit

  def getInstanceLog(instanceId: String): Option[Either[URL, String]]

  def getTaskLog(id: String): Option[Either[URL, String]]

  def getMessage(queue: String, id: String): Option[Either[URL, String]]

  def terminateInstance(id: String): Try[Unit]

  def sshInstance(id: String): Try[Unit]

  def workersInfo(nispero: String): NodeSeq


  def name: String
  def sendUndeployCommand(reason: String, force: Boolean): Unit

  def mainCSS: String
  def mainHTML: String

}
