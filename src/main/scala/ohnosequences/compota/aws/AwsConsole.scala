package ohnosequences.compota.aws


import ohnosequences.compota.console.Console
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

  override def getInstanceLog(instanceId: InstanceId): Try[String] = {

  }

  override def compotaInfoPageDetailsTable: NodeSeq = ???

  override def getInstanceStackTrace(id: InstanceId): Try[String] = ???

  override def getSSHInstance(id: InstanceId): Try[String] = ???

  override def getTerminateInstance(id: InstanceId): Try[String] = ???

  override def nisperoInfoDetails(nispero: N): NodeSeq = ???

  override def listWorkers(nispero: String, lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ListWorkerInfo])] = ???

  override type ListWorkerInfo = this.type

  override def shutdown(): Unit = ???

  override def getNamespaceLog(id: String): Try[Either[URL, String]] = ???

  override def getInstanceLogRaw(instanceId: String): Try[Either[URL, String]] = ???
}
