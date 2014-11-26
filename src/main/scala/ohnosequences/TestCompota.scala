package ohnosequences

import ohnosequences.compota.{Instructions, Compota, Nispero, MapInstructions}
import ohnosequences.compota.logging.Logger
import ohnosequences.compota.queues.local.BlockingQueue

import scala.util.Success

object q1 extends BlockingQueue[Int](10)
object q2 extends BlockingQueue[String](10)

object instr extends MapInstructions[Int, String] {
  override def apply(logger: Logger, context: Int, input: Int) = Success((1000 / input).toString)

  override def prepare(logger: Logger) = Success(0)

  override type Context = Int
}

object nispero1 extends Nispero("printer", q1, q2, instr)

object TestCompota extends Compota(List(nispero1)) {
  override def addTasks(): Unit = {
    q1.getWriter.foreach{_.write(List("1" -> 123, "2" -> 0))}
  }
}
