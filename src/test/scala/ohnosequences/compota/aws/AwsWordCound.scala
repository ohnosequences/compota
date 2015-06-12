package ohnosequences.compota.aws

import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.compota.aws.deployment.{AnyMetadata, Metadata}
import ohnosequences.compota.aws.queues.DynamoDBQueue
import ohnosequences.compota.environment.Env
import ohnosequences.compota.local.LocalNispero
import ohnosequences.compota.serialization.{intSerializer, stringSerializer}
import ohnosequences.compota.{InMemoryQueueReducer, Instructions}
import ohnosequences.compota.monoid.{stringMonoid, intMonoid}
import ohnosequences.logging.{ConsoleLogger, Logger}
import org.junit.Test
import org.junit.Assert._
import scala.concurrent.duration._

import scala.concurrent.duration.Duration
import scala.util.{Success, Try}

class AwsWordCount {

  object wordLengthInstructions extends Instructions[String, Int] {

    override type Context = Unit

    override def solve(env: Env, context: Unit, input: String): Try[List[Int]] = {
      throw new Error("uuu!")
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

  val textQueue = new DynamoDBQueue[String](
    name = "text",
    serializer = stringSerializer
  )
  val wordsQueue = new DynamoDBQueue[String](
    name = "words",
    serializer = stringSerializer
  )

  val countsQueue = new DynamoDBQueue[Int](
    name = "counts",
    serializer = intSerializer
  )

  val rawMetadata = ohnosequences.compota.test.generated.metadata

  val compotaMetadata = new Metadata(
    rawMetadata.artifact,
    rawMetadata.jarUrl,
    rawMetadata.testJarUrl,
    Some(this.getClass.toString)
  )

  object wordCountCompotaConfiguration extends AwsCompotaConfiguration {


    override def metadata: AnyMetadata = compotaMetadata


    override def logUploaderTimeout: Duration = Duration(1, MINUTES)

    override val loggerDebug: Boolean = true
    override val deleteErrorQueue: Boolean = false
    override val timeout: Duration = Duration(1, HOURS)

  }



  object splitNisperoConfiguration extends  AwsNisperoConfiguration {

    override def name: String = "split"

    override def compotaConfiguration: AwsCompotaConfiguration = wordCountCompotaConfiguration

    override def workerDesiredSize: Int = 1
  }

  val splitNispero = AwsNispero(
    textQueue,
    wordsQueue,
    splitInstructions,
    splitNisperoConfiguration)

  object wordLengthNisperoConfiguration extends AwsNisperoConfiguration {
    override def name: String = "wordLenght"

    override def compotaConfiguration: AwsCompotaConfiguration = wordCountCompotaConfiguration
    override def workerDesiredSize: Int = 1
  }

  val wordLengthNispero = AwsNispero(
    wordsQueue,
    countsQueue,
    wordLengthInstructions,
    splitNisperoConfiguration
  )


  val reducer = InMemoryQueueReducer.create(countsQueue, intMonoid)

  object wordCountCompota extends AwsCompota[Int](List(splitNispero, wordLengthNispero), List(reducer), wordCountCompotaConfiguration) {

    val s = System.currentTimeMillis()

    override def prepareUnDeployActions(env: AwsEnvironment): Try[Int] = Success(1000)


    override def configurationChecks(env: AwsEnvironment): Try[Boolean] = {
      Try{
        configuration.metadata.testJarUrl.exists { s =>
          ObjectAddress(s).map { obj =>
            env.awsClients.s3.objectExists(obj, Some(env.logger))
          }.getOrElse(false)
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
      Success("message context = " +context)
    }
  }

  @Test
  def localCompotaTest(): Unit = {

    //wordCountCompota()



   // wordCountCompota.launch()
   // wordCountCompota.waitForFinished()


    //  wordCountCompota.main(Array("add", "tasks"))
    // wordCountCompota.launchWorker(splitNispero)

  }

}
