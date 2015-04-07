package ohnosequences.compota.local

import ohnosequences.compota.Instructions
import ohnosequences.compota.local.BlockingQueue
import ohnosequences.compota.monoid.{stringMonoid, intMonoid}
import ohnosequences.logging.Logger
import org.junit.Test
import org.junit.Assert._

import scala.util.{Success, Try}

class LocalCompotaTest {

  object wordLengthInstructions extends Instructions[String, Int] {

    override type Context = Unit

    override def solve(logger: Logger, context: Unit, input: String): Try[List[Int]] = {
      Success(List(input.length))
    }

    override def prepare(logger: Logger) = Success(())

    override val name: String = "wordLength"
  }

  object splitInstructions extends Instructions[String, String] {

    override type Context = Unit

    override def solve(logger: Logger, context: Unit, input: String): Try[List[String]] = {
      Success(input.split("\\s+").toList))
    }

    override def prepare(logger: Logger) = Success(())

    override val name: String = "split"
  }

  val textQueue = new BlockingQueue[String]("text", 2000, stringMonoid)
  val wordsQueue = new BlockingQueue[String]("words", 2000, stringMonoid)
  val countsQueue = new BlockingQueue[Int]("counts", 2000, intMonoid)

  object splitNispero extends LocalNispero(wordLengthInstructions.name, textQueue, wordsQueue, splitInstructions)

  object wordLengthNispero extends LocalNispero(wordLengthInstructions.name, wordsQueue, countsQueue, wordLengthInstructions)

  object wordCountCompotaConfiguration extends LocalCompotaConfiguration {
    override val loggerDebug: Boolean = true
  }

  object wordCountCompota extends LocalCompota(List(splitNispero, wordLengthNispero), List(countsQueue), wordCountCompotaConfiguration)

  @Test
  def localCompotaTest(): Unit = {



  }

}
