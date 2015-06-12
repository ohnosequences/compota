package ohnosequences.compota.aws


import ohnosequences.compota.console.{AnyWorkerInfo, Console}
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

  override def listWorkers(nispero: String, lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ListWorkerInfo])] = ???

  class AwsWorkerInfo extends AnyWorkerInfo {
    override def instanceId: InstanceId = ???
  }

  override type ListWorkerInfo = AwsWorkerInfo

  override def printLog(instanceId: String, namespace: String): NodeSeq = ???


  override def printNamespaceTable(): NodeSeq = ???

  override def shutdown(): Unit = ???

  override def terminateInstance(instanceId: String): NodeSeq = ???

  override def getLogRaw(instanceId: String, namespace: String): Try[Either[URL, String]] = ???

  override def sidebar: NodeSeq = ???

  override def sshInstance(instanceId: String): NodeSeq = xml.NodeSeq.Empty

  override def stackTraceInstance(instanceId: String): NodeSeq = ???
}
