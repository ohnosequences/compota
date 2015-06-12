package ohnosequences.compota.local

import java.util.concurrent.atomic.AtomicReference

import ohnosequences.compota.environment.Env
import ohnosequences.compota.graphs.QueueChecker
import ohnosequences.compota.{TerminationDaemon, InMemoryQueueReducerLocal, InMemoryQueueReducer, Instructions}
import ohnosequences.compota.monoid.{stringMonoid, intMonoid}
import ohnosequences.logging.{ConsoleLogger, Logger}
import org.junit.Test
import org.junit.Assert._
import scala.concurrent.duration._

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object wordLengthInstructions extends Instructions[String, Int] {

  override type Context = Unit

  override def solve(env: Env, context: Unit, input: String): Try[List[Int]] = {
    Success(List(input.length))
  }

  override def prepare(env: Env) = Success(())

}

object splitInstructions extends Instructions[String, String] {

  override type Context = Unit

  override def solve(env: Env, context: Unit, input: String): Try[List[String]] = {
   // Thread.sleep(200000)
    Success(input.split("\\s+").toList)
  }

  override def prepare(env: Env) = Success(())

}

object textQueue extends LocalQueue[String]("text", visibilityTimeout = Duration(60, SECONDS))
object wordsQueue extends LocalQueue[String]("words", visibilityTimeout = Duration(60, SECONDS))
object countsQueue extends LocalQueue[Int]("counts")



object wordCountCompotaConfiguration extends AnyLocalCompotaConfiguration {
  override def name: String = "wordCount"
  override val loggerDebug: Boolean = true
  override val timeout= Duration(100, SECONDS)
  override val terminationDaemonIdleTime =  Duration(10, SECONDS)
  override val visibilityTimeout: Duration = Duration(15, SECONDS)

  override def errorThreshold: Int = 3
}

object splitNispero extends LocalNisperoLocal (
  textQueue,
  wordsQueue,
  splitInstructions,
  LocalNisperoConfiguration(wordCountCompotaConfiguration, "split", 5)
)

object wordLengthNispero extends LocalNisperoLocal (
  wordsQueue,
  countsQueue,
  wordLengthInstructions,
  LocalNisperoConfiguration(wordCountCompotaConfiguration, "lenght", 5)
)



class LocalCompotaTest {

  val result = new AtomicReference[Int]()

  val input = List("a a a b b cc cc")

  object reducer extends InMemoryQueueReducerLocal(countsQueue, intMonoid, result)

  object wordLenghtCompota extends LocalCompota[Int](List(splitNispero, wordLengthNispero), List(reducer), wordCountCompotaConfiguration) {


    val s = System.currentTimeMillis() + 1

    override def prepareUnDeployActions(env: wordLenghtCompota.CompotaEnvironment): Try[Int] = {
      Failure(new Error("intentional"))

      //Success(1000)
    }

    override def addTasks(env: CompotaEnvironment): Try[Unit] = {
      env.logger.debug("test")
     // while(true) {
     //   Thread.sleep(1000)
       // environment.logger.info(controlQueue.rawQueue.toString)
       // environment.logger.info(controlQueue.rawQueueP.toString)

    //  }
      // environment.logger.error(new Error("exception"))
      val op = textQueue.create(env.localContext).get
      val writer = op.writer.get
      writer.writeMessages("1", input)
     // env.logger.info(env.errorTable.listErrors(None, None).toString)
      Failure(new Error("intentional"))

    }


//    override def launchTerminationDaemon(graph: QueueChecker[CompotaEnvironment], env: CompotaEnvironment): Try[TerminationDaemon[LocalCompotaTest.this.wordLenghtCompota.CompotaEnvironment]] = {
//      while(true) {
//        env.logger.info("waiting")
//        env.logger.info(controlQueue.rawQueue.toString)
//        env.logger.info(controlQueue.rawQueueP.toString)
//        Thread.sleep(5000)
//      }
//      Failure(new Error("e"))
//    }



    override def unDeployActions(env: LocalEnvironment, context: Int): Try[String] = {
      env.logger.info("waiting")
      env.logger.info(metaManager.controlQueue.rawQueue.toString)
      env.logger.info(metaManager.controlQueue.rawQueueP.toString)
      Thread.sleep(5000)
      Success("message context = " +context)

    }
  }


  @Test
  def localCompotaTest(): Unit = {
    println("test")
    wordLenghtCompota.launch()

    wordLenghtCompota.waitForFinished()

    val expectedResult = input.flatMap(_.split("\\s+").toList).map(_.length).sum
    assertEquals(expectedResult, result.get())

  //  wordCountCompota.main(Array("add", "tasks"))
   // wordCountCompota.launchWorker(splitNispero)

  }

}
