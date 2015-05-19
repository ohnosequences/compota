package ohnosequences.compota.local

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ConcurrentSkipListMap}

import scala.concurrent.{ExecutionContext, Future}


import ohnosequences.compota.queues._
import ohnosequences.logging.Logger

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.duration.Duration._

import scala.util.{Random, Failure, Success, Try}

import scala.collection.JavaConversions._

case class MonkeyAppearanceProbability(
                                       create: Double = 0,
                                       reader: Double = 0,
                                       writer: Double = 0,
                                       isEmpty: Double = 0,
                                       delete: Double = 0,
                                       list: Double = 0,
                                       read: Double = 0,
                                       size: Double = 0,
                                       receiveMessage: Double = 0,
                                       deleteMessage: Double = 0,
                                       writeRaw: Double = 0,
                                       getBody: Double = 0,
                                       reducer: Double = 0
                                        )

case class LocalMessage[T](id: String, body: T, monkeyAppearanceProbability: MonkeyAppearanceProbability) extends QueueMessage[T] {
  override def getBody: Try[Option[T]] = {
    Monkey.call(Success(Some(body)), monkeyAppearanceProbability.getBody)
  }

  val deleted = new AtomicBoolean(false)
}

object Monkey {
  val random = new Random()
  def call[T](statement: => Try[T], errorProbability: Double): Try[T] = {
    if (random.nextDouble() < errorProbability) {
      //error
      Failure(new Error("monkey was here"))
    } else {
      statement
    }
  }
}

class LocalQueue[T](name: String,
                    val monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability(),
                    val visibilityTimeout: Duration = Duration(30, SECONDS))
  extends Queue[T, LocalContext](name) { queue =>

  val rawQueue = new ConcurrentSkipListMap[String, T]()
  val rawQueueP = new ConcurrentSkipListMap[String, T]()

  type Msg = LocalMessage[T]

  type Reader = LocalQueueReader[T]
  type Writer = LocalQueueWriter[T]

  override def create(ctx: LocalContext): Try[QueueOp[T, LocalMessage[T], LocalQueueReader[T], LocalQueueWriter[T]]] = {
    Monkey.call(Success(new LocalQueueOp[T](queue, ctx)), monkeyAppearanceProbability.create)
  }
}

class LocalQueueOp[T](val queue: LocalQueue[T], val ctx: LocalContext) extends QueueOp[T, LocalMessage[T], LocalQueueReader[T], LocalQueueWriter[T]] { queueOp =>

  override def deleteMessage(message: LocalMessage[T]): Try[Unit] = {

    Monkey.call(Try {
      message.deleted.set(true)
      queue.rawQueueP.remove(message.id)
      queue.rawQueue.remove(message.id)
    }, queue.monkeyAppearanceProbability.deleteMessage)
  }

  override def reader: Try[LocalQueueReader[T]] = {
    Monkey.call(Success(new LocalQueueReader(queueOp)), queue.monkeyAppearanceProbability.reader)
  }

  override def writer: Try[LocalQueueWriter[T]] = {
    Monkey.call(Success(new LocalQueueWriter(queueOp)), queue.monkeyAppearanceProbability.writer)
  }

  override def isEmpty: Try[Boolean] = {
    Monkey.call(Success(queue.rawQueueP.isEmpty), queue.monkeyAppearanceProbability.isEmpty)
  }

  override def delete(): Try[Unit] = {
    Monkey.call(Success(()), queue.monkeyAppearanceProbability.delete)
  }

  //todo add limit support
  override def list(lastKey: Option[String], limit: Option[Int]): Try[(Option[String], List[String])] = {
    Monkey.call(Success((None, queue.rawQueueP.keySet().toList)), queue.monkeyAppearanceProbability.list)
  }

  override def size: Try[Int] = {
    Monkey.call(Success(queue.rawQueueP.size()), queue.monkeyAppearanceProbability.size)
  }

  override def get(key: String): Try[T] = {
    ctx.logger.info("getting " + key)
    Monkey.call(
      Option(queue.rawQueueP.get(key)) match {
        case None => Failure(new Error("key " + key + " doesn't exist in " + queue.name))
        case Some(v) => Success(v)
      },
      queue.monkeyAppearanceProbability.read
    )
  }
}

class LocalQueueReader[T](val queueOp: LocalQueueOp[T]) extends QueueReader[T, LocalMessage[T]] {

  override def receiveMessage(logger: Logger): Try[Option[LocalMessage[T]]] = {
    Option(queueOp.queue.rawQueue.pollFirstEntry()) match {
      case None => Success(None)
      case Some(entry) => {
        val  res = new LocalMessage(entry.getKey, entry.getValue, queueOp.queue.monkeyAppearanceProbability)
        queueOp.ctx.executor.execute { new Runnable {
          override def toString: String = queueOp.queue.name + " message extender for " + entry.getKey

          override def run(): Unit = {
            Thread.sleep(queueOp.queue.visibilityTimeout.toMillis)
            if(!res.deleted.get()) {
              logger.info("return message to queue: " + entry.getValue)
              queueOp.queue.rawQueue.put(entry.getKey, entry.getValue)
            }
          }
        }

        }
//        Future {
//
//        }(ExecutionContext.fromExecutor(queueOp.ctx.executor))
        Success(Some(res))
      }
    }
  }

}

class LocalQueueWriter[T](queueOp: LocalQueueOp[T]) extends QueueWriter[T] {

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]

  override def writeRaw(values: List[(String, T)]) = {
    Monkey.call(Try {
      values.foreach { case (key, value) =>
        queueOp.queue.rawQueue.put(key, value)
        queueOp.queue.rawQueueP.put(key, value)
      }
    }, queueOp.queue.monkeyAppearanceProbability.writeRaw)
  }
}

