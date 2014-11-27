package ohnosequences.compota.queues


import scala.util.{Try, Failure}



case class ProductQueue[X, Y](xQueue: Queue[X], yQueue: Queue[Y])
  extends Queue[(X, Y)](xQueue.name + "_" + yQueue.name) {

  class ProductMessage(val m1: xQueue.Message, val m2: yQueue.Message) extends QueueMessage[(X, Y)] {
    override def getBody: Try[(X, Y)] = Try{ m1.getBody.get -> m2.getBody.get }

    override def getId: Try[String] = Try{ m1.getId.get + "," + m2.getId.get }
  }

  override type Message = ProductMessage

  override def deleteMessage(message: ProductMessage): Try[Unit] = Try {
    xQueue.deleteMessage(message.m1).get
    yQueue.deleteMessage(message.m2).get
  }
  override type QW = ProductQueueWriter
  override type QR = UnitReader.type

  override def getWriter: Try[QW] = Try{new ProductQueueWriter(xQueue.getWriter.get, yQueue.getWriter.get)}

  override def getReader: Try[QR] = UnitReader.getMessage


  override def isEmpty: Boolean = xQueue.isEmpty && yQueue.isEmpty

  //todo fix name
  class ProductQueueWriter(xWriter: xQueue.QW, yWriter: yQueue.QW) extends QueueWriter[(X, Y), ProductMessage] {
    override def write(prefixId: String, values: List[(X, Y)]) = {
      val l1  = values.zipWithIndex.map { case (value, i) =>
        (prefixId + "._1." + (i+1), value._1)
      }
      val l2  = values.zipWithIndex.map { case (value, i) =>
        (prefixId + "._1." + (i+1), value._2)
      }
      Try {
        xWriter.writeRaw(l1).get
        yWriter.writeRaw(l2).get
      }
    }

    def writeRaw(values: List[(String, (X, Y))]) = Failure(new Error("not implemented"))

//    def write(values: List[(String, (X, Y))]) = Try {
//      val l1 = values.map { case (id, (x, y)) =>
//        (id, x)
//      }
//      val l2 = values.map { case (id, (x, y)) =>
//        (id, y)
//      }
//      xWriter.write(l1)
//      yWriter.write(l2)
//    }
  }

  object UnitReader extends QueueReader[(X, Y), ProductMessage] {
    override def getMessage = Failure(new Error("can't read from ProductQueue"))
  }


}



  object ProductQueue {
  def flatQueue(queue: QueueAux): List[QueueAux] = {
    queue match {
      case ProductQueue(q1, q2) => flatQueue(q1) ++ flatQueue(q2)
      case q => List(q)
    }
  }
}