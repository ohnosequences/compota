package ohnosequences.compota.aws

import java.security.Timestamp

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ScalarAttributeType, AttributeDefinition}
import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.dynamodb.{RepeatConfiguration, DynamoDBUtils}
import ohnosequences.compota.{ErrorTableItem, ErrorTable, Namespace}
import ohnosequences.compota.environment.InstanceId
import ohnosequences.logging.Logger

import scala.collection.JavaConversions._
import scala.util.{Success, Failure, Try}


class AwsErrorTable(logger: Logger, tableName: String, aws: AWSClients) extends ErrorTable {

  import AwsErrorTable._

  def reportError(errorTableItem: ErrorTableItem): Try[Unit] = {
    val item = marshallErrorTableItem(errorTableItem)
    DynamoDBUtils.putItem(aws.ddb, Some(logger), tableName, item, RepeatConfiguration(attemptThreshold = 10))
  }

  def getNamespaceErrorCount(namespace: Namespace): Try[Int] = {
    DynamoDBUtils.countKeysPerHash(aws.ddb, Some(logger), tableName, hashKeyName, hashKey(namespace), RepeatConfiguration(attemptThreshold = 10))
  }

  def recover(): Try[Unit] = {
    logger.info("recovering error table")
    AwsErrorTable(logger, tableName, aws).map { r => ()}
  }



//  override def listErrorsPerNamespace(namespace: Namespace, lastToken: Option[(String, String)], limit: Option[Int]): Try[(Option[(String, String)], List[ErrorTableItem])] = {
//    DynamoDBUtils.queryPerHash(
//      aws.ddb,
//      Some(logger),
//      tableName,
//      hashKeyName,
//      hashKey(namespace),
//      Seq(rangeKeyName, message, stackTrace),
//      RepeatConfiguration(attemptThreshold = 10)
//    ).map { items =>
//      (None, )
//    }
//  }
  override def listErrors(lastToken: Option[(String, String)], limit: Option[Int]): Try[(Option[(String, String)], List[ErrorTableItem])] = {
    DynamoDBUtils.list(
      aws.ddb,
      Some(logger),
      tableName,
      lastKey = lastToken.map(marshallLastToken),
      attributesToGet = Seq(hashKeyName, rangeKeyName, message, stackTrace),
      limit,
      RepeatConfiguration(attemptThreshold = 10)
    ).map { case (lastTokenItem, items) =>
      (lastTokenItem.map(unMarshallLastToken), items.map(unMarshallErrorTableItem))
    }
  }
}

object AwsErrorTable {

  val errorTableError = "error table error"

  val hashKeyName = "nameSpace"
  val rangeKeyName = "instanceTime"
  val message = "message"
  val stackTrace = "stackTrace"

  val hashAttribute = new AttributeDefinition().withAttributeName(hashKeyName).withAttributeType(ScalarAttributeType.S)
  val rangeAttribute = new AttributeDefinition().withAttributeName(rangeKeyName).withAttributeType(ScalarAttributeType.S)

  def marshallLastToken(lastToken: (String, String)): Map[String, AttributeValue] = {
    Map(
      hashKeyName -> new AttributeValue().withS(lastToken._1),
      rangeKeyName -> new AttributeValue().withS(lastToken._2)
    )
  }

  def unMarshallLastToken(lastKey: Map[String, AttributeValue]): (String, String) = {
    (lastKey.get(hashKeyName).map(_.getS).getOrElse(""), lastKey.get(rangeKeyName).map(_.getS).getOrElse(""))
  }


  def marshallErrorTableItem(item: ErrorTableItem): Map[String, AttributeValue] = {
    Map[String, AttributeValue](
      hashKeyName -> hashKey(item.namespace),
      rangeKeyName -> rangeKey(item.timestamp, item.instanceId),
      message ->  new AttributeValue().withS(item.message),
      stackTrace -> new AttributeValue().withS(item.stackTrace)
    )
  }

  def unMarshallErrorTableItem(item: Map[String, AttributeValue]): ErrorTableItem = {
    val (timestamp: Long, instanceId: String) = item.get(rangeKeyName).map { s =>
      s.getS.split('#').toList match {
        case t :: i :: Nil => (t.toLong, i)
        case _ => (0, "")
      }
    }.getOrElse((0, ""))


    ErrorTableItem(
      namespace = new Namespace(item.get(hashKeyName).map(_.getS).getOrElse("")),
      instanceId = InstanceId(instanceId),
      timestamp = timestamp,
      message = item.get(message).map(_.getS).getOrElse(""),
      stackTrace = item.get(stackTrace).map(_.getS).getOrElse("")
    )
  }


  def hashKey(namespace: Namespace): AttributeValue = {
    new AttributeValue().withS(namespace.toString)
  }

  def rangeKey(timestamp: Long, instanceId: InstanceId): AttributeValue = {
    new AttributeValue().withS(System.currentTimeMillis() + "#" + instanceId.id)
  }

  def apply(logger: Logger, tableName: String, aws: AWSClients): Try[AwsErrorTable] = {
      Try {DynamoDBUtils.createTable(
          ddb = aws.ddb,
          tableName = tableName,
          hash = hashAttribute,
          range = Some(rangeAttribute),
          logger = logger,
          waitForCreation = true
        )
      }.flatMap {created =>
        if(!created) {
          Failure(new Error("can't create error table"))
        } else {
          Success(new AwsErrorTable(logger, tableName, aws))
        }
      }
  }


}
