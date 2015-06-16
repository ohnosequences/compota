package ohnosequences.compota.local

import ohnosequences.compota.console.Pagination

import scala.collection.JavaConversions._
import java.util.concurrent.ConcurrentHashMap

import ohnosequences.compota.environment.InstanceId
import ohnosequences.compota.{ErrorTableItem, Namespace, ErrorTable}

import scala.util.{Success, Failure, Try}

/**
 * Created by Evdokim on 18.05.2015.
 */
class LocalErrorTable extends ErrorTable {

  val errors = new ConcurrentHashMap[(Namespace, (Long, InstanceId)), (String, String)]()

  override def reportError(errorTableItem: ErrorTableItem): Try[Unit] = {
    Try {
      errors.put(errorTableItem.key, errorTableItem.value)
    }
  }

  override def listErrors(lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ErrorTableItem])] = {
    Try {
      val errorsList = errors.toList.map { case (key, value) =>
        ErrorTableItem(key, value)
      }
      Pagination.listPagination(errorsList, limit, lastToken)
    }
  }


  override def recover(): Try[Unit] = {
    Success(())
  }

  override def getError(namespace: Namespace, timestamp: Long, instanceId: InstanceId): Try[ErrorTableItem] = {
    val key = (namespace, (timestamp, instanceId))
    Option(errors.get(key)) match {
      case None => Failure(new Error("key " + key + " not found"))
      case Some(value) => {
        Success(ErrorTableItem(key, value))
      }
    }
  }

//  def printErrors(): Unit = {
//    errors.forEach { case (namespace, )}
//  }

  override def getNamespaceErrorCount(namespace: Namespace): Try[Int] = {
    Try{
      //print("looking for " + namespace + "in" + errors.map
      errors.count(_._1._1.equals(namespace))
    }
  }
}
