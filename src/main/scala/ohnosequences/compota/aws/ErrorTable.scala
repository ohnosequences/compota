package ohnosequences.compota.aws

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ScalarAttributeType, AttributeDefinition}
import ohnosequences.awstools.AWSClients
import ohnosequences.awstools.dynamodb.DynamoDBUtils
import ohnosequences.compota.Namespace
import ohnosequences.compota.environment.InstanceId
import ohnosequences.logging.Logger

import scala.collection.JavaConversions._
import scala.util.{Success, Failure, Try}


class ErrorTable(logger: Logger, tableName: String, aws: AWSClients) {

  import ErrorTable._

  def fail(nameSpace: Namespace, instanceId: InstanceId, message: String): Try[Unit] = {
    val item = Map[String, AttributeValue](
      hashKeyName -> hashKey(nameSpace),
      rangeKeyName -> rangeKey(instanceId),
      bodyKeyName ->  new AttributeValue().withS(message)
    )
    DynamoDBUtils.putItem(aws.ddb, logger, tableName, item, 10)
  }

  def getNameSpaceErrorsCount(nameSpace: Namespace): Try[Int] = {
    DynamoDBUtils.countKeysPerHash(aws.ddb, logger, tableName, hashKeyName, hashKey(nameSpace), 5)
  }

  def recover(): Try[Unit] = {
    logger.info("recovering error table")
    ErrorTable(logger, tableName, aws).map { r => ()}
  }

}

object ErrorTable {

  val errorTableError = "error table error"

  val hashKeyName = "nameSpace"
  val rangeKeyName = "instanceTime"
  val bodyKeyName = "message"

  val hashAttribute = new AttributeDefinition().withAttributeName(hashKeyName).withAttributeType(ScalarAttributeType.S)
  val rangeAttribute = new AttributeDefinition().withAttributeName(rangeKeyName).withAttributeType(ScalarAttributeType.S)

  def hashKey(namespace: Namespace): AttributeValue = {
    new AttributeValue().withS(namespace.toString)
  }

  def rangeKey(instanceId: InstanceId): AttributeValue = {
    new AttributeValue().withS(instanceId.id + ":" + System.currentTimeMillis())
  }

  def apply(logger: Logger, tableName: String, aws: AWSClients): Try[ErrorTable] = {
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
          Success(new ErrorTable(logger, tableName, aws))
        }
      }
  }
}
