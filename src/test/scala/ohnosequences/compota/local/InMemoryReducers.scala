package ohnosequences.compota.local

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

import ohnosequences.compota.environment.Env
import ohnosequences.compota.monoid.{Monoid, stringMonoid, intMonoid}
import ohnosequences.compota.queues.{InMemoryReducible}
import ohnosequences.logging.{Logger, ConsoleLogger}
import org.junit.Test
import org.junit.Assert._

import scala.util.Failure

/**
 * Created by Evdokim on 17.06.2015.
 */
class InMemoryReducersTest {

  //@Test
  def inMemoryReducerTest(): Unit = {
    object queue extends LocalQueue[String]("test") with InMemoryReducible {
      override val monoid: Monoid[String] = stringMonoid
    }

    val env = new Env {
      override val logger: Logger = new ConsoleLogger("inMemoryReducerTest")

      override def isStopped: Boolean = false

      override val workingDirectory: File = new File(".")
    }

    val localContext = new LocalContext(Executors.newCachedThreadPool(), env.logger)
    val result = new AtomicReference[String]()
    queue.create(localContext).flatMap { queueOp =>
      queueOp.writer.flatMap { writer =>
        for (i <- 1 to 1000) {
          writer.writeMessages("id_" + i, List("m" + i))
        }
        env.logger.info("reducing " + queue.name)
        queueOp.reduce(env)

      }
    }.recoverWith { case t =>
      org.junit.Assert.fail(t.toString)
      Failure(t)
    }
//    queue.create(localContext).flatMap { queueOp =>
//      queueOp.
//    }


  }

}
