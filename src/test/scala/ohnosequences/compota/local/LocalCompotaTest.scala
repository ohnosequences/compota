package ohnosequences.compota.local

import ohnosequences.compota.{InMemoryQueueReducer, Instructions}
import ohnosequences.compota.monoid.{stringMonoid, intMonoid}
import ohnosequences.logging.{ConsoleLogger, Logger}
import org.junit.Test
import org.junit.Assert._

import scala.util.{Success, Try}

class LocalCompotaTest {

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

  val textQueue = new LocalQueue[String]("text")
  val wordsQueue = new LocalQueue[String]("words")
  val countsQueue = new LocalQueue[Int]("counts")

  val splitNispero = LocalNispero(
    textQueue,
    wordsQueue,
    splitInstructions, 2)

  val wordLengthNispero = LocalNispero(
    wordsQueue,
    countsQueue,
    wordLengthInstructions, 2
  )

  object wordCountCompotaConfiguration extends LocalCompotaConfiguration {
    override val loggerDebug: Boolean = false
  }


  @Test
  def localCompotaTest(): Unit = {
    val logger = new ConsoleLogger("localCompotaTest", debug = false)

    val reducer = InMemoryQueueReducer(countsQueue, intMonoid)

    object wordCountCompota extends LocalCompota(List(splitNispero, wordLengthNispero), List(reducer), wordCountCompotaConfiguration) {
      override def addTasks(environment: CompotaEnvironment): Try[Unit] = {
        environment.logger.debug("test")
       // environment.logger.error(new Error("exception"))
        val op = textQueue.create(()).get
        val writer = op.writer.get
        writer.writeRaw(List(("1", "a a a b b")))
      }

      override def unDeployActions(force: Boolean, env: ThreadEnvironment): Try[Unit] = {
        Success(env.logger.info("undeploying force=" + force))
      }
    }

    wordCountCompota.launch()
    wordCountCompota.waitForFinished()


  //  wordCountCompota.main(Array("add", "tasks"))
   // wordCountCompota.launchWorker(splitNispero)

  }

}
