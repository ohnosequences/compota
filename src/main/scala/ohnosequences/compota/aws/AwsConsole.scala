package ohnosequences.compota.aws


import ohnosequences.compota.Namespace
import ohnosequences.compota.console.{AnyEnvironmentInfo, Console}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.graphs.QueueChecker
import ohnosequences.compota.queues.AnyQueueOp

import scala.util.Try
import scala.xml.NodeSeq

import java.net.URL



class AwsConsole[N <: AnyAwsNispero](awsCompota: AnyAwsCompota.ofN[N],
                                         env: AwsEnvironment,
                                         controlQueueOp: AnyQueueOp,
                                         queueChecker: QueueChecker[AwsEnvironment]) extends
Console[AwsEnvironment, N, AnyAwsCompota.ofN[N]](awsCompota, env, controlQueueOp, queueChecker) {
  override def compotaInfoPageDetailsTable: NodeSeq = ???

  override def nisperoInfoDetails(nispero: N): NodeSeq = ???


  class AwsWorkerInfo extends AnyEnvironmentInfo {
    override def printState: NodeSeq = ???

    override def namespace: Namespace = ???

    override def instanceId: InstanceId = ???
  }


  override type EnvironmentInfo = AwsWorkerInfo

  override def printLog(instanceId: String, namespace: Seq[String]): NodeSeq = ???

  override def shutdown(): Unit = ???

  override def getLogRaw(instanceId: String, namespace: Seq[String]): Try[Either[URL, String]] = ???

  override def sidebar: NodeSeq = ???

  override def stackTraceInstance(instanceId: String, namespace: Seq[String]): NodeSeq = ???

  override def listNisperoWorkers(nispero: String, lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[EnvironmentInfo])] = ???

  override def namespacePage: NodeSeq = ???

  override def printNamespaces(lastToken: Option[String]): NodeSeq = ???

  override def terminateInstance(instanceId: String, namespace: Seq[String]): NodeSeq = ???

  override def sshInstance(instanceId: String, namespace: Seq[String]): NodeSeq = ???
}
