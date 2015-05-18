package ohnosequences.compota.local

import ohnosequences.compota.{InMemoryQueueReducerLocal, InMemoryQueueReducer, Instructions}
import ohnosequences.compota.monoid.{stringMonoid, intMonoid}
import ohnosequences.logging.{ConsoleLogger, Logger}
import org.junit.Test
import org.junit.Assert._
import scala.concurrent.duration._

import scala.concurrent.duration.Duration
import scala.util.{Success, Try}

object wordLengthInstructions extends Instructions[String, Int] {

  override type Context = Unit

  override def solve(logger: Logger, context: Unit, input: String): Try[List[Int]] = {
    // throw new Error("uuu!")
    Success(List(input.length))
  }

  override def prepare(logger: Logger) = Success(())

}

object splitInstructions extends Instructions[String, String] {

  override type Context = Unit

  override def solve(logger: Logger, context: Unit, input: String): Try[List[String]] = {
    Thread.sleep(100000)
    Success(input.split("\\s+").toList)
  }

  override def prepare(logger: Logger) = Success(())

}

object textQueue extends LocalQueue[String]("text", visibilityTimeout = Duration(5, SECONDS))
object wordsQueue extends LocalQueue[String]("words", visibilityTimeout = Duration(5, SECONDS))
object countsQueue extends LocalQueue[Int]("counts")

object splitNispero extends LocalNisperoLocal (
  textQueue,
  wordsQueue,
  splitInstructions,
  new LocalNisperoConfiguration("split", 5)
)

object wordLengthNispero extends LocalNisperoLocal (
  wordsQueue,
  countsQueue,
  wordLengthInstructions,
  new LocalNisperoConfiguration("lenght", 5)
)

object wordCountCompotaConfiguration extends LocalCompotaConfiguration("wordCount") {

  override val loggerDebug: Boolean = false

  override val timeout= Duration(100, SECONDS)
  override val terminationDaemonIdleTime =  Duration(10, SECONDS)
}


object reducer extends InMemoryQueueReducerLocal(countsQueue, intMonoid)

object wordCountCompota extends LocalCompota[Int](List(splitNispero, wordLengthNispero), List(reducer), wordCountCompotaConfiguration) {



  val s = System.currentTimeMillis() + 1

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

class LocalCompotaTest {


  @Test
  def localCompotaTest(): Unit = {
    wordCountCompota.launch()

    wordCountCompota.waitForFinished()

  //  wordCountCompota.main(Array("add", "tasks"))
   // wordCountCompota.launchWorker(splitNispero)

  }

}
