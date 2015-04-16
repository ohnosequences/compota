package ohnosequences.compota.aws

import ohnosequences.compota.aws.deployment.Metadata
import ohnosequences.compota.aws.queues.DynamoDBQueue
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

    override def solve(logger: Logger, context: Unit, input: String): Try[List[Int]] = {
      throw new Error("uuu!")
      Success(List(input.length))
    }

    override def prepare(logger: Logger) = Success(())

    override val name: String = "wordLength"
  }

  object splitInstructions extends Instructions[String, String] {

    override type Context = Unit

    override def solve(logger: Logger, context: Unit, input: String): Try[List[String]] = {
      Success(input.split("\\s+").toList)
    }

    override def prepare(logger: Logger) = Success(())

    override val name: String = "split"
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

  val metadata = new Metadata("artifact", "jar_url")

  object wordCountCompotaConfiguration extends AwsCompotaConfiguration(metadata) {

    override val loggerDebug: Boolean = true
    override val deleteErrorQueue: Boolean = false
    override val timeout: Duration = Duration(1, HOURS)
  }



  object splitNisperoConfiguration extends  AwsNisperoConfiguration("split", wordCountCompotaConfiguration) {
    override def workerDesiredSize: Int = 1
  }

  val splitNispero = AwsNispero(
    textQueue,
    wordsQueue,
    splitInstructions,
    splitNisperoConfiguration)

  object wordLengthNisperoConfiguration extends AwsNisperoConfiguration("wordLenght", wordCountCompotaConfiguration) {
    override def workerDesiredSize: Int = 1
  }

  val wordLengthNispero = AwsNispero(
    wordsQueue,
    countsQueue,
    wordLengthInstructions,
    splitNisperoConfiguration
  )



  @Test
  def localCompotaTest(): Unit = {
    val logger = new ConsoleLogger("localCompotaTest", debug = false)

    val reducer = InMemoryQueueReducer.create(countsQueue, intMonoid)

    object wordCountCompota extends AwsCompota[Int](List(splitNispero, wordLengthNispero), List(reducer), wordCountCompotaConfiguration) {

      val s = System.currentTimeMillis()

      override def prepareUnDeployActions(env: wordCountCompota.CompotaEnvironment): Try[Int] = Success(1000)

      override def addTasks(environment: AwsEnvironment): Try[Unit] = {
        environment.logger.debug("test")
        // environment.logger.error(new Error("exception"))
        val op = textQueue.create(environment.createDynamoDBContext()).get
        val writer = op.writer.get
        writer.writeRaw(List(("1", "a a a b b")))
      }

      override def unDeployActions(force: Boolean, env: AwsEnvironment, context: Int): Try[String] = {
        Success("message context = " +context)

      }
    }

   // wordCountCompota.launch()
   // wordCountCompota.waitForFinished()


    //  wordCountCompota.main(Array("add", "tasks"))
    // wordCountCompota.launchWorker(splitNispero)

  }

}
