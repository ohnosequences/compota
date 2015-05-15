package ohnosequences.compota

import java.security.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import ohnosequences.compota.environment.InstanceId

import scala.util.Try

case class ErrorTableItem(namespace: Namespace, timestamp: Long, instanceId: InstanceId,  message: String, stackTrace: String) {

  val format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss")

  def formattedTime(): String = {
    format.format(new Date(timestamp))
  }


}

trait ErrorTable {

  //def reportError(namespace: Namespace, instanceId: InstanceId, timestamp: Long, message: String): Try[Unit]

  def reportError(errorTableItem: ErrorTableItem): Try[Unit]

  def getNamespaceErrorCount(namespace: Namespace): Try[Int]

  def listErrors(lastToken: Option[(String, String)], limit: Option[Int]): Try[(Option[(String, String)], List[ErrorTableItem])]

  def getError(namespace: Namespace, timestamp: Long, instanceId: InstanceId): Try[ErrorTableItem]


}
