package ohnosequences.nisperon.queues

import ohnosequences.logging.ConsoleLogger

import scala.collection.JavaConversions._


class VisibilityExtender[T](sqsQueue: SQSQueue[T]) extends Thread("extender_" + sqsQueue.name) {

  val logger = new ConsoleLogger("VisibilityExtender")

  val messages = new java.util.concurrent.ConcurrentHashMap[String, SQSMessage[T]]()


  def addMessage(m: SQSMessage[T]) {
    messages.put(m.receiptHandle, m)
  }

  def clear() {
    messages.clear()
  }

  def deleteMessage(receiptHandler: String) {
   // try {
      messages.remove(receiptHandler)
   // } catch {
   //   messages
   // }
  }

  override def run() {

    while (true) {
      messages.values().foreach {
        m =>
          try {
           // logger.info("extending")
            m.changeMessageVisibility(sqsQueue.visibilityTimeout.getOrElse(100))
          } catch {
            case t: Throwable => {
              println("warning: invalid id " + t.getLocalizedMessage)
              messages.remove(m.receiptHandle)
            }
          }
      }
      Thread.sleep(50 * 1000)
    }
  }
}