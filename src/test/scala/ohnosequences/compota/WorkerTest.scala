package ohnosequences.compota

import ohnosequences.compota.environment.{Environment, ThreadEnvironment}
import ohnosequences.compota.monoid.intMonoid
import ohnosequences.compota.queues.local.BlockingQueue
import ohnosequences.compota.worker.Worker
import ohnosequences.logging.Logger
import org.junit.Test
import org.junit.Assert._

import scala.annotation.tailrec
import scala.util.{Failure, Try, Success}

class WorkerTest {

  object testInstructions extends Instructions[Int, Int] {
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
    val input = new BlockingQueue[Int]("in", 2000, intMonoid)

    val output = new BlockingQueue[Int]("out", 2000, intMonoid)

    var expectedResult = 0

    val n = 1000
    val inputOp = input.create(()).get
    val writer = inputOp.writer.get

    for (i <- 1 to n) {
      expectedResult += i*i + 1
      writer.writeMessages("i", List(i))
    }

    val worker = new Worker[Int, Int, Unit, BlockingQueue[Int], BlockingQueue[Int]](input, output, testInstructions)

    val outputOp = output.create(()).get

    object workerThread extends Thread("worker") {
      val env = new ThreadEnvironment(this)
      override def run(): Unit = {
        worker.start(env)
      }
    }

    workerThread.start()

    while(!input.rawQueue.isEmpty) {
      println("input queue size: " + inputOp.size + " output: " + outputOp.size)

      Thread.sleep(1000)
    }


    input.writeEmptyMessage()
    Thread.sleep(1000)
    workerThread.env.kill()


    val reader = outputOp.reader.get

    assertEquals(n, outputOp.size)

    var realSumm = 0

    for (i <- 1 to 1000) {
      realSumm += reader.receiveMessage.get.body
    }

    assertEquals(expectedResult, realSumm)




  }

}
