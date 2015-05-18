package ohnosequences.compota

import java.security.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import ohnosequences.compota.environment.InstanceId

import scala.util.Try

object ErrorTableItem {
  def apply(key: (Namespace, (Long, InstanceId)), value: (String, String)): ErrorTableItem = {
    ErrorTableItem(key._1, key._2._1, key._2._2, value._1, value._2)
  }


  def keyToToken(key: (Namespace, (Long, InstanceId))): String = {
    key._1.toString() + "#" + key._2._1.toString + "#" + key._2._2.id
  }


  def keyToPairToken(key: (Namespace, (Long, InstanceId))): (String, String) = {
    (key._1.toString(), key._2._1.toString + "#" + key._2._2.id)
  }

  def parseToken(token: String): (Namespace, (Long, InstanceId)) = {
    token.split('#').toList match {
      case namespace :: timestamp :: instanceId :: Nil => (Namespace(namespace), (timestamp.toLong, InstanceId(instanceId)))
      case _ => (Namespace(""), (0, InstanceId("")))
    }
  }
}
case class ErrorTableItem(namespace: Namespace, timestamp: Long, instanceId: InstanceId,  message: String, stackTrace: String) {

  val format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss")

  def formattedTime(): String = {
    format.format(new Date(timestamp))
  }

  def key: (Namespace, (Long, InstanceId)) = (namespace, (timestamp, instanceId))


  def token: String = {
    namespace.toString() + "#" + timestamp.toString + "#" + instanceId.id
  }

  def pairToken: (String, String) = {
    (namespace.toString(), timestamp.toString + "#" + instanceId.id)
  }

  def value: (String, String) = (message, stackTrace)

}

trait ErrorTable {

  def reportError(namespace: Namespace,  timestamp: Long, instanceId: InstanceId, message: String, stackTrace: String): Try[Unit] = {
    reportError(ErrorTableItem(namespace, timestamp, instanceId, message, stackTrace ))
  }



  def reportError(errorTableItem: ErrorTableItem): Try[Unit]

  def getNamespaceErrorCount(namespace: Namespace): Try[Int]

  def listErrors(lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ErrorTableItem])]

  def getError(namespace: Namespace, timestamp: Long, instanceId: InstanceId): Try[ErrorTableItem]


}
