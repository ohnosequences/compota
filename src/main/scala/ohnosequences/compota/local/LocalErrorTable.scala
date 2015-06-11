package ohnosequences.compota.local

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
   // println(errors)
    def listErrors(errors: List[((Namespace, (Long, InstanceId)), (String, String))]): Try[(Option[String], List[ErrorTableItem])] = {
      Try {
        limit match {
          case Some(l) if l < errors.size - 1 => {
            val key = errors(l)._1
            val token = ErrorTableItem.keyToToken(key)
            val items = errors.take(l).map { case (k, value) =>
              ErrorTableItem(k, value)
            }
            (Some(token), items)
          }
          case _ => {
            (None, errors.map { case (k, value) =>
              ErrorTableItem(k, value)
            })
          }
        }
      }
    }

    lastToken match {
      case None => listErrors(errors.toList)
      case Some(lToken) => {
        val list = errors.toList
        val newErrors = list.drop(list.indexWhere { case (key, value) =>
          ErrorTableItem.keyToToken(key).equals(lToken)
        } + 1)
        listErrors(newErrors)
      }
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
