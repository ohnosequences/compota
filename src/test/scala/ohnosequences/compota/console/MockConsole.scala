package ohnosequences.compota.console

import java.net.URL

import ohnosequences.logging.Logger

import scala.util.Try
import scala.xml.NodeSeq

class MockConsole extends Console {

  override def password: String = "password"

  override def getTaskLog(id: String): Option[Either[URL, String]] = ???

  override def shutdown(): Unit = ???

  override def getMessage(queue: String, id: String): Option[Either[URL, String]] = ???

  override def terminateInstance(id: String): Try[Unit] = ???

  override def getInstanceLog(instanceId: String): Option[Either[URL, String]] = ???

  override def mainCSS: String = ???

  override def workersInfo(nispero: String): NodeSeq = ???

  override def sendUndeployCommand(reason: String, force: Boolean): Unit = ???

  override def name: String = ???

  override def nisperoInfoDetails(nisperoName: String): NodeSeq = ???

  override def listMessages(queueName: String, last: Option[String]): NodeSeq = ???

  override def listErrors(last: Option[(String, String)]): NodeSeq = ???

  override def sshInstance(id: String): Try[Unit] = ???

  override def compotaInfoPageDetails: NodeSeq = ???

  override def mainHTML: String = ???

  override def queueStatus(name: String): NodeSeq = ???

  override val logger: Logger = _
}
