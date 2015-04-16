package ohnosequences.compota.local

import ohnosequences.compota.{InMemoryQueueReducer, Instructions}
import ohnosequences.compota.monoid.{stringMonoid, intMonoid}
import ohnosequences.logging.{ConsoleLogger, Logger}
import org.junit.Test
import org.junit.Assert._
import scala.concurrent.duration._

import scala.concurrent.duration.Duration
import scala.util.{Success, Try}

class LocalCompotaTest {

  object wordLengthInstructions extends Instructions[String, Int] {

    override type Context = Unit

    override def solve(logger: Logger, context: Unit, input: String): Try[List[Int]] = {
     // throw new Error("uuu!")
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

  val textQueue = new LocalQueue[String]("text", visibilityTimeout = Duration(5, SECONDS))
  val wordsQueue = new LocalQueue[String]("words", visibilityTimeout = Duration(5, SECONDS))
  val countsQueue = new LocalQueue[Int]("counts")

  val splitNispero = LocalNispero(
    textQueue,
    wordsQueue,
    splitInstructions, 1)

  val wordLengthNispero = LocalNispero(
    wordsQueue,
    countsQueue,
    wordLengthInstructions, 1
  )

  object wordCountCompotaConfiguration extends LocalCompotaConfiguration {
    override val loggerDebug: Boolean = true

    override val timeout= Duration(100, SECONDS)
    override val terminationDaemonIdleTime =  Duration(10, SECONDS)
  }


  //@Test
  def localCompotaTest(): Unit = {
    val logger = new ConsoleLogger("localCompotaTest", debug = false)

    val reducer = InMemoryQueueReducer(countsQueue, intMonoid)

    object wordCountCompota extends LocalCompota[Int](List(splitNispero, wordLengthNispero), List(reducer), wordCountCompotaConfiguration) {


      val s = System.currentTimeMillis()

      override def prepareUnDeployActions(env: wordCountCompota.CompotaEnvironment): Try[Int] = Success(1000)

      override def addTasks(environment: CompotaEnvironment): Try[Unit] = {
        environment.logger.debug("test")
       // environment.logger.error(new Error("exception"))
        val op = textQueue.create(environment.localContext).get
        val writer = op.writer.get
        writer.writeRaw(List(("1", "a a a b b")))
      }

      override def unDeployActions(force: Boolean, env: LocalEnvironment, context: Int): Try[String] = {
        Success("message context = " +context)

      }
    }

    wordCountCompota.launch()
    wordCountCompota.waitForFinished()
    logger.info("test finished")


  //  wordCountCompota.main(Array("add", "tasks"))
   // wordCountCompota.launchWorker(splitNispero)

  }

}
