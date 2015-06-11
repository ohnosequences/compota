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


  override def getError(namespace: Namespace, timestamp: Long, instanceId: InstanceId): Try[ErrorTableItem] = {
    DynamoDBUtils.getItem(
      aws.ddb,
      Some(logger),
      tableName,
      marshallKey((namespace, (timestamp, instanceId))),
      Seq(hashKeyName, rangeKeyName, message, stackTrace),
      RepeatConfiguration(attemptThreshold = 10)
    ).flatMap {
      case Some(item) => Success(unMarshallErrorTableItem(item))
      case None => Failure(new Error("couldn't find error with namespace: " + namespace.toString))
    }
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
  override def listErrors(lastToken: Option[String], limit: Option[Int]): Try[(Option[String], List[ErrorTableItem])] = {
    val lastKey = lastToken.map { token =>
      marshallKey(ErrorTableItem.parseToken(token))
    }
    DynamoDBUtils.list(
      aws.ddb,
      Some(logger),
      tableName,
      lastKey = lastKey,
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



  def unMarshallLastToken(lastKey: Map[String, AttributeValue]): String = {
    lastKey.get(hashKeyName).map(_.getS).getOrElse("") + "#" + lastKey.get(rangeKeyName).map(_.getS).getOrElse("")
  }


  def marshallErrorTableItem(item: ErrorTableItem): Map[String, AttributeValue] = {
    Map[String, AttributeValue](
      hashKeyName -> hashKey(item.pairToken),
      rangeKeyName -> rangeKey(item.pairToken),
      message ->  new AttributeValue().withS(item.message),
      stackTrace -> new AttributeValue().withS(item.stackTrace)
    )
  }


  def marshallKey(key: (Namespace, (Long, InstanceId))): Map[String, AttributeValue] = {
    val token = ErrorTableItem.keyToPairToken(key)
    Map(
      hashKeyName -> hashKey(token),
      rangeKeyName -> rangeKey(token)
    )
  }


  def unMarshallErrorTableItem(item: Map[String, AttributeValue]): ErrorTableItem = {

    val token = unMarshallLastToken(item)
    val key = ErrorTableItem.parseToken(token)

    ErrorTableItem(
      key = key,
      value = (
        item.get(message).map(_.getS).getOrElse(""),
        item.get(stackTrace).map(_.getS).getOrElse("")
      )
    )
  }

  def hashKey(namespace: Namespace): AttributeValue = {
    new AttributeValue().withS(namespace.toString)
  }

  def hashKey(token: (String, String)): AttributeValue = {
    new AttributeValue().withS(token._1)
  }

  def rangeKey(token: (String, String)): AttributeValue = {
    new AttributeValue().withS(token._2)
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
