package ohnosequences.compota

import ohnosequences.compota.aws.MonkeyQueue
import ohnosequences.compota.environment.{Environment, ThreadEnvironment}
import ohnosequences.compota.monoid.intMonoid
import ohnosequences.compota.worker.Worker
import ohnosequences.logging.Logger
import org.junit.Test
import org.junit.Assert._

import scala.annotation.tailrec
import scala.util.{Failure, Try, Success}

class MonkeyWorkerTest {

  object monkeyInstructions extends Instructions[Int, Int] {
    override type Context = Int

    override def solve(logger: Logger, context: Int, input: Int): Try[List[Int]] = {
      Success(List(input * input + context))
    }

    override def prepare(logger: Logger): Try[Int] = {
      Success(1)
    }

    override val name: String = "square"
  }

  @Test
  def workerTest(): Unit = {
    val input = new MonkeyQueue[Int]("in", 2000, intMonoid)

    val output = new MonkeyQueue[Int]("out", 2000, intMonoid)

    var expectedResult = 0

    val n = 1000
    val inputOp = input.create(()).get
    val writer = inputOp.writer.get

    val inputData = (1 to n).toList


    inputData.foreach { i =>
      expectedResult += i*i + 1
    }

    writer.writeMessages("i", inputData)


    val worker = new Worker[Int, Int, Unit, MonkeyQueue[Int], MonkeyQueue[Int]](input, output, monkeyInstructions)

    val outputOp = output.create(()).get

    object workerThread extends Thread("worker") {
      val env = new ThreadEnvironment(this)
      override def run(): Unit = {
        worker.start(env)
      }
    }

    workerThread.start()

    while(!inputOp.isEmpty) {
      println("input queue size: " + inputOp.size + " output: " + outputOp.size)

      Thread.sleep(1000)
    }


    inputOp.writeEmptyMessage()
    Thread.sleep(1000)
    workerThread.env.kill()



    val reader = outputOp.reader.get

    assertEquals(n, outputOp.size)

    var realSumm = 0

    for (i <- 1 to 1000) {
      val res = reader.receiveMessage.get.body
      realSumm += res
    }



    assertEquals(expectedResult, realSumm)




  }

}
