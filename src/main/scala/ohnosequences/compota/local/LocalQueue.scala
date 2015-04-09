package ohnosequences.compota.local

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ConcurrentSkipListMap, ConcurrentHashMap}

import ohnosequences.compota.queues._
import ohnosequences.logging.Logger

import scala.annotation.tailrec
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
  override def getBody: Try[T] = {
    Monkey.call(Success(body), monkeyAppearanceProbability.getBody)
  }
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

class LocalQueue[T](name: String, val monkeyAppearanceProbability: MonkeyAppearanceProbability = MonkeyAppearanceProbability()) extends Queue[T, Unit](name) { queue =>

  val rawQueue = new ConcurrentSkipListMap[String, T]()

  type Msg = LocalMessage[T]

  type Reader = LocalQueueReader[T]
  type Writer = LocalQueueWriter[T]

  override def create(ctx: Unit): Try[QueueOp[T, LocalMessage[T], LocalQueueReader[T], LocalQueueWriter[T]]] = {
    Monkey.call(Success(new LocalQueueOp[T](queue)), monkeyAppearanceProbability.create)
  }
}

class LocalQueueOp[T](val queue: LocalQueue[T]) extends QueueOp[T, LocalMessage[T], LocalQueueReader[T], LocalQueueWriter[T]] { queueOp =>

  override def deleteMessage(message: LocalMessage[T]): Try[Unit] = {
    Monkey.call(Try {queue.rawQueue.remove(message.id)}, queue.monkeyAppearanceProbability.deleteMessage)
  }

  override def reader: Try[LocalQueueReader[T]] = {
    Monkey.call(Success(new LocalQueueReader(queueOp)), queue.monkeyAppearanceProbability.reader)
  }

  override def writer: Try[LocalQueueWriter[T]] = {
    Monkey.call(Success(new LocalQueueWriter(queueOp)), queue.monkeyAppearanceProbability.writer)
  }

  override def isEmpty: Try[Boolean] = {
    Monkey.call(Success(queue.rawQueue.isEmpty), queue.monkeyAppearanceProbability.isEmpty)
  }

  override def delete(): Try[Unit] = {
    Monkey.call(Success(()), queue.monkeyAppearanceProbability.delete)
  }

  //todo add limit support
  override def list(lastKey: Option[String], limit: Option[Int]): Try[(Option[String], List[String])] = {
    Monkey.call(Success((None, queue.rawQueue.keySet().toList)), queue.monkeyAppearanceProbability.list)
  }

  override def size: Try[Int] = {
    Monkey.call(Success(queue.rawQueue.size()), queue.monkeyAppearanceProbability.size)
  }

  override def read(key: String): Try[T] = {
    Monkey.call(Success(queue.rawQueue.get(key)), queue.monkeyAppearanceProbability.read)
  }
}

class LocalQueueReader[T](queueOps: LocalQueueOp[T]) extends QueueReader[T, LocalMessage[T]] {

  override def receiveMessage(logger: Logger, isStopped: => Boolean = {  false}): Try[LocalMessage[T]] = {
    @tailrec
    def receiveMessageRec: Try[LocalMessage[T]] = {
      if (isStopped) {
        Failure(new Error("client was stopped"))
      } else {
        Option(queueOps.queue.rawQueue.pollFirstEntry()) match {
          case None => {
            //map is empty
            //try again
            logger.debug("queue is empty: " + queueOps.queue.rawQueue.isEmpty)
            Thread.sleep(1000)
            receiveMessageRec
          }
          case Some(entry) => Success(new LocalMessage(entry.getKey, entry.getValue, queueOps.queue.monkeyAppearanceProbability))
        }
      }
    }

    Monkey.call(receiveMessageRec, queueOps.queue.monkeyAppearanceProbability.receiveMessage)


  }
}

class LocalQueueWriter[T](queueOp: LocalQueueOp[T]) extends QueueWriter[T] {

  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]

  override def writeRaw(values: List[(String, T)]) = {
    Monkey.call(Try {
      values.foreach { case (key, value) =>
        queueOp.queue.rawQueue.put(key, value)
      }
    }, queueOp.queue.monkeyAppearanceProbability.writeRaw)
  }
}

