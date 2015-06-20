package ohnosequences.compota.test.aws

import com.amazonaws.auth.AWSCredentialsProvider
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.compota.aws._
import ohnosequences.compota.aws.deployment.{AnyMetadata, Metadata}
import ohnosequences.compota.aws.queues.{S3InMemoryReducible, DynamoDBQueue}
import ohnosequences.compota.environment.Env
import ohnosequences.compota.serialization.{intSerializer, stringSerializer}
import ohnosequences.compota.Instructions
import ohnosequences.compota.monoid.{Monoid, stringMonoid, intMonoid}
import ohnosequences.logging.ConsoleLogger
import org.junit.Assert._
import org.junit.Test
import scala.concurrent.duration._

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object wordLengthInstructions extends Instructions[String, Int] {
  override type Context = Unit

  override def solve(env: Env, context: Unit, input: String): Try[List[Int]] = {
    // throw new Error("uuu!")
    Success(List(input.length))
  }

  override def prepare(env: Env) = Success(())
}

object splitInstructions extends Instructions[String, String] {
  override type Context = Unit

  override def solve(env: Env, context: Unit, input: String): Try[List[String]] = {
    Success(input.split("\\s+").toList)
  }

  override def prepare(env: Env) = Success(())
}

object textQueue extends DynamoDBQueue[String](
  name = "text",
  serializer = stringSerializer
)

object wordsQueue extends DynamoDBQueue[String](
  name = "words",
  serializer = stringSerializer
)

object countsQueue extends DynamoDBQueue[Int](
  name = "counts",
  serializer = intSerializer
) with S3InMemoryReducible {

  override val destination: Option[ObjectAddress] = wordCountCompotaConfiguration.resultsDestination(countsQueue)
  override val monoid = intMonoid

}

object wordCountCompotaConfiguration extends AwsCompotaConfiguration {
  override def metadata: AnyMetadata = AwsCompotaTest.testMetadata.copy(mainClass = Some("ohnosequences.compota.test.aws.wordCountCompota"))

  override def localAwsCredentialsProvider: AWSCredentialsProvider = AwsCompotaTest.testCredentialsProvider

  override def notificationEmail: String = AwsCompotaTest.testNotificationEmail

  override def loggerDebug: Boolean = true

  override val deleteErrorQueue: Boolean = false
  override val timeout: Duration = Duration(1, HOURS)
}

object splitNisperoConfiguration extends AwsNisperoConfiguration {
  override def name: String = "split"

  override def compotaConfiguration: AwsCompotaConfiguration = wordCountCompotaConfiguration

  override def workerDesiredSize: Int = 1
}

object splitNispero extends AwsNispero(
  textQueue,
  wordsQueue,
  splitInstructions,
  splitNisperoConfiguration
)

object wordLengthNisperoConfiguration extends AwsNisperoConfiguration {
  override def name: String = "wordLenght"

  override def compotaConfiguration: AwsCompotaConfiguration = wordCountCompotaConfiguration

  override def workerDesiredSize: Int = 1
}

object wordLengthNispero extends AwsNispero(
  wordsQueue,
  countsQueue,
  wordLengthInstructions,
  splitNisperoConfiguration
)

object wordCountCompota extends AwsCompota[Int](List(splitNispero, wordLengthNispero), wordCountCompotaConfiguration) {

  override def prepareUnDeployActions(env: AwsEnvironment): Try[Int] = Success(1000)

  override def configurationChecks(env: AwsEnvironment): Try[Boolean] = {
    super.configurationChecks(env).flatMap { u =>
      env.logger.info("checking test jar " + configuration.metadata.testJarUrl)
      configuration.metadata.testJarUrl match {
        case None => Failure(new Error("test jar URL is not specified"))
        case Some(jarLocation) => {
          ObjectAddress(jarLocation).flatMap { jarObject =>
            env.awsClients.s3.objectExists(jarObject).flatMap {
              case true => Success(true)
              case false => Failure(new Error("test jar " + jarObject.url + " does not exists"))
            }
          }
        }
      }
    }
  }

  override def addTasks(environment: AwsEnvironment): Try[Unit] = {
    environment.logger.debug("test")
    // environment.logger.error(new Error("exception"))
    val op = textQueue.create(environment.createDynamoDBContext).get
    val writer = op.writer.get
    writer.writeRaw(List(("1", "a a a b b")))
  }

  override def unDeployActions(env: AwsEnvironment, context: Int): Try[String] = {
    Success("message context = " + context)
  }
}

class AwsWordCountTest {
  @Test
  def awsWordCoundTest(): Unit = {
    val cliLogger = new ConsoleLogger("awsWordCountTest")
    cliLogger.info("started")
    wordCountCompota.localEnvironment(cliLogger, List[String]()).flatMap { env =>
      cliLogger.info("local environment loaded")
      cliLogger.info("checking configuration for " + wordCountCompota)
      wordCountCompota.configurationChecks(env).flatMap { r =>
        cliLogger.info("checks passed. launching compota")
        wordCountCompota.launch(env).map { e =>
          cliLogger.info("started")
        }
       // Success(())
      }
    }.recoverWith { case t =>
      cliLogger.error(t)
      org.junit.Assert.fail(t.toString)
      Failure(t)
    }

  }

}
