package ohnosequences.compota.queues


import ohnosequences.compota.monoid.Monoid
import ohnosequences.logging.Logger

import scala.util.{Success, Try, Failure}


trait AnyProductMessage extends AnyQueueMessage {
  type XElement
  type YElement


  override type QueueMessageElement = (XElement, YElement)

  type XMessage <: AnyQueueMessage.of[XElement]
  val xMessage: Option[XMessage]

  type YMessage <: AnyQueueMessage.of[YElement]
  val yMessage: Option[YMessage]

  val xMonoid: Monoid[XElement]
  val yMonoid: Monoid[YElement]

  override val id: String = {
    xMessage.map(_.id).getOrElse("") + "#" + yMessage.map(_.id).getOrElse("")
  }

  override def getBody: Try[Option[(XElement, YElement)]] = {

    val xValue = xMessage match {
      case None => {
        Success(Some(xMonoid.unit))
      }
      case Some(xmsg) => {
        xmsg.getBody
      }
    }

    val yValue = yMessage match {
      case None => {
        Success(Some(yMonoid.unit))
      }
      case Some(ymsg) => {
        ymsg.getBody
      }
    }

    (xValue, yValue) match {
      case (Failure(t), _) => Failure(t)
      case (_, Failure(t)) => Failure(t)
      case (Success(None), Success(None)) => Success(None) //deleted from both queues
      case (Success(Some(xv)), Success(Some(yv))) => Success(Some((xv, yv)))
      case (Success(Some(xv)), Success(None)) => Success(Some((xv, yMonoid.unit)))
      case (Success(None), Success(Some(yv))) => Success(Some((xMonoid.unit, yv)))
    }
  }
}

//class ProductMessage[X, Y, XMsg <: AnyQueueMessage.of[X], YMsg <: AnyQueueMessage.of[Y]](
//                                                                                          val xMessage: Option[XMsg],
//                                                                                          val yMessage: Option[YMsg],
//                                                                                          val xMonoid: Monoid[X],
//                                                                                          val yMonoid: Monoid[Y]) extends AnyProductMessage {
//  override type XElement = X
//  override type YElement = Y
//  override type XMessage = XMsg
//  override type YMessage = YMsg
//}

//class ProductMessage[X, Y, Ctx, XMsg <: QueueMessage[X], YMsg <: QueueMessage[Y],
//                     XR <: QueueReader[X, XMsg], XW <: QueueWriter[X],
//                     YR <: QueueReader[Y, YMsg], YW <: QueueWriter[Y],
//                     XQueue <: Queue.of[X, Ctx, XMsg, XR, XW], YQueue <: Queue.of[Y, Ctx, YMsg, YR, YW]](
//                                                                                   val xQueue: XQueue,
//                                                                                   val yQueue: YQueue,
//                                                                                   val xMonoid: Monoid[X],
//                                                                                   val yMonoid: Monoid[Y],
//                                                                                   val xMessage: Option[XMsg],
//                                                                                   val yMessage: Option[YMsg])
//  extends QueueMessage[(X, Y)] {
//
//}
//

trait AnyProductQueue extends AnyQueue { anyproductQueue =>
  type XElement
  type YElement

  val xMonoid: Monoid[XElement]
  val yMonoid: Monoid[YElement]

  type XMessage <: AnyQueueMessage.of[XElement]
  type YMessage <: AnyQueueMessage.of[YElement]

  type QueueContext

  type XReader <: AnyQueueReader.of[XElement, XMessage]
  type XWriter <: AnyQueueWriter.of[XElement]

  type YReader <: AnyQueueReader.of[YElement, YMessage]
  type YWriter <: AnyQueueWriter.of[YElement]

  type XQueueOp <: AnyQueueOp.of[XElement, XMessage, XReader, XWriter]
  type YQueueOp <: AnyQueueOp.of[YElement, YMessage, YReader, YWriter]

  type XQueue <: AnyQueue.of3[XElement, QueueContext, XMessage, XReader, XWriter, XQueueOp]
  val xQueue: XQueue

  type YQueue <: AnyQueue.of3[YElement, QueueContext, YMessage, YReader, YWriter, YQueueOp]

  val yQueue: YQueue

  override type QueueElement = (XElement, YElement)

  override type QueueQueueMessage = ProductMessage

  override type QueueQueueReader = ProductQueueReader
  override type QueueQueueWriter = ProductQueueWriter
  override type QueueQueueOp = ProductQueueOp


  override def subQueues: List[AnyQueue] = xQueue.subQueues ++ yQueue.subQueues

  override def create(ctx: QueueContext): Try[QueueQueueOp] = {

    xQueue.create(ctx).flatMap { xQueueOp =>
      yQueue.create(ctx).map { yQueueOp =>
        new ProductQueueOp(anyproductQueue, xQueueOp, yQueueOp)
      }
    }
  }

  class ProductMessage(val message: Either[XMessage, YMessage]) extends AnyQueueMessage {

    override type QueueMessageElement = (XElement, YElement)

    override val id: String = {
      message match {
        case Left(xm) => xm.id
        case Right(ym) => ym.id
      }
    }

    override def getBody: Try[Option[(XElement, YElement)]] = {
      message match {
        case Left(xm) => {
          xm.getBody.map {
            case None => None
            case Some(xb) => Some((xb, yMonoid.unit))
          }
        }
        case Right(ym) => {
          ym.getBody.map {
            case None => None
            case Some(yb) => Some((xMonoid.unit, yb))
          }
        }
      }
    }
  }

  class ProductQueueReader(val queueOp: AnyQueueOp, val xReader: XReader, val yReader: YReader ) extends AnyQueueReader {

    override type QueueReaderElement = (XElement, YElement)
    override type QueueReaderMessage = ProductMessage

    override def receiveMessage(logger: Logger): Try[Option[QueueReaderMessage]] = {
      if (scala.util.Random.nextBoolean()) {
        xReader.receiveMessage(logger).map {
          case None => None
          case Some(xmsg) => Some(new ProductMessage(Left(xmsg)))
        }
      } else {
        yReader.receiveMessage(logger).map {
          case None => None
          case Some(ymsg) => Some(new ProductMessage(Right(ymsg)))
        }
      }
    }
  }

  class ProductQueueWriter(val queueOp: AnyQueueOp, val xWriter: XWriter, val yWriter: YWriter) extends AnyQueueWriter {
    override type QueueWriterElement =  (XElement, YElement)

    override def writeRaw(values: List[(String, QueueWriterElement)]): Try[Unit] = {
      Try {
        val xMessages = values.map { case (id, (x, y)) =>
          (id + "_1", x)
        }
        xWriter.writeRaw(xMessages).get
        val yMessages = values.map { case (id, (x, y)) =>
          (id + "_2", y)
        }
        yWriter.writeRaw(yMessages).get
      }
    }
  }

  class ProductQueueOp(val queue: AnyQueue, val xQueueOp: XQueueOp, val yQueueOp: YQueueOp) extends AnyQueueOp { productQueueOp =>


    override def subOps(): List[AnyQueueOp] = xQueueOp.subOps() ++ yQueueOp.subOps()

    override type QueueOpElement = (XElement, YElement)
    override type QueueOpQueueMessage = ProductMessage

    override def deleteMessage(message: QueueOpQueueMessage): Try[Unit] = {
      message.message match {
        case Left(xmsg) => xQueueOp.deleteMessage(xmsg)
        case Right(ymsg) => yQueueOp.deleteMessage(ymsg)
      }
    }

    override def writer: Try[QueueOpQueueWriter] = {
      xQueueOp.writer.flatMap { xWriter =>
        yQueueOp.writer.map { yWriter =>
          new ProductQueueWriter(productQueueOp, xWriter, yWriter)
        }
      }
    }

    override def reader: Try[QueueOpQueueReader] = {
      xQueueOp.reader.flatMap { xReader =>
        yQueueOp.reader.map { yReader =>
          new ProductQueueReader(productQueueOp, xReader, yReader)
        }
      }
    }

    //not agreed with write!!!
    override def get(key: String): Try[QueueOpElement] = {
      xQueueOp.get(key) match {
        case Failure(t) => yQueueOp.get(key).map { yEl =>
          (xMonoid.unit, yEl)
        }
        case Success(xEl) =>  Success((xEl, yMonoid.unit))
      }
    }

    override def size: Try[Int] = {
      xQueueOp.size.flatMap { xSize =>
        yQueueOp.size.map { ySize =>
          xSize + ySize
        }
      }
    }

    override def delete(): Try[Unit] = {
      xQueueOp.delete().flatMap { xr =>
        yQueueOp.delete()
      }
    }

    //todo add second queue
    override def list(lastKey: Option[String], limit: Option[Int]): Try[(Option[String], List[String])] = {
      xQueueOp.list(lastKey, limit)
    }

    override def isEmpty: Try[Boolean] = {
      xQueueOp.isEmpty.flatMap { xIsEmpty =>
        yQueueOp.isEmpty.map { yIsEmpty =>
          xIsEmpty && yIsEmpty
        }
      }
    }

    override type QueueOpQueueWriter = ProductQueueWriter

    override type QueueOpQueueReader = ProductQueueReader
  }

}

//trait AnyProductQueueReader extends AnyQueueReader {
//  type XElement
//  type YElement
//
//  val xMonoid: Monoid[XElement]
//  val yMonoid: Monoid[YElement]
//
//  type XMessage <: AnyQueueMessage.of[XElement]
//  type YMessage <: AnyQueueMessage.of[YElement]
//
//
//  type XReader <: AnyQueueReader.of[XElement, XMessage]
//  val xReader: XReader
//
//  type YReader <: AnyQueueReader.of[YElement, YMessage]
//  val yReader: YReader
//
//  override type QueueReaderElement = (XElement, YElement)
//
//  override type QueueReaderMessage = ProductMessage[XElement, YElement, XMessage, YMessage]
//
//
//  override def receiveMessage(logger: Logger): Try[Option[QueueReaderMessage]] = {
//    xReader.receiveMessage(logger).flatMap {
//      case None => {
//        yReader.receiveMessage(logger).map {
//          case None => None
//          case Some(ymsg) => Some(new ProductMessage[XElement, YElement, XMessage, YMessage](None, Some(ymsg), xMonoid, yMonoid))
//        }
//      }
//      case Some(xmsg) => {
//        yReader.receiveMessage(logger).map {
//          case None => Some(new ProductMessage[XElement, YElement, XMessage, YMessage](Some(xmsg), None, xMonoid, yMonoid))
//          case Some(ymsg) => Some(new ProductMessage(Some(xmsg), Some(ymsg), xMonoid, yMonoid))
//        }
//      }
//    }
//  }
//}



class ProductQueue[Ctx, X, Y,
XM <: AnyQueueMessage.of[X], XR <: AnyQueueReader.of[X, XM], XW <: AnyQueueWriter.of[X], XO <: AnyQueueOp.of[X, XM, XR, XW], XQ <: AnyQueue.of3[X, Ctx, XM, XR, XW, XO],
YM <: AnyQueueMessage.of[Y], YR <: AnyQueueReader.of[Y, YM], YW <: AnyQueueWriter.of[Y], YO <: AnyQueueOp.of[Y, YM, YR, YW], YQ <: AnyQueue.of3[Y, Ctx, YM, YR, YW, YO]
](val xQueue: XQ, val yQueue: YQ, val xMonoid: Monoid[X], val yMonoid: Monoid[Y]) extends AnyProductQueue {

  override type XElement = X
  override type YElement = Y


  val name = xQueue.name + "_" + yQueue.name

  override type QueueContext = Ctx



  override type XMessage = XM
  override type YMessage = YM

  override type XReader = XR
  override type YReader = YR

  override type XWriter = XW
  override type YWriter = YW

  override type XQueueOp = XO
  override type YQueueOp = YO

  override type XQueue = XQ
  override type YQueue = YQ


}

//class ProductQueue2[Ctx, X, Y, XQ <: AnyQueue.of2m[Ctx, X], YQ <: AnyQueue.of2m[Ctx, Y]](val xQueue2: XQ, val yQueue2: YQ) extends AnyProductQueue {
//
//  override type XElement = xQueue2.QueueElement
//  override type YElement = yQueue2.QueueElement
//
//  override val name = xQueue2.name + "_" + yQueue2.name
//
//  override type QueueContext = Ctx
//
//  override type XMessage = xQueue2.QueueQueueMessage
//  override type YMessage = yQueue2.QueueQueueMessage
//
//  override type XReader = xQueue2.QueueQueueReader
//  override type YReader = yQueue2.QueueQueueReader
//
//  override type XWriter = xQueue2.QueueQueueWriter
//  override type YWriter = yQueue2.QueueQueueWriter
//
//  override type XQueueOp = xQueue2.QueueQueueOp
//  override type YQueueOp = yQueue2.QueueQueueOp
//
//  override type XQueue = xQueue2.type
//  override type YQueue = yQueue2.type
//
//  override def xQueue =xQueue2
//  override def yQueue =yQueue2
//
//  override val xMonoid: Monoid[XElement] = xQueue.monoid
//  override val yMonoid: Monoid[XElement] = yQueue.monoid
//}
//}
//class AnyProductQueueOp extends AnyQueueOp {
//
//}

//class ProductQueue[X, Y, Ctx, XMsg <: QueueMessage[X], YMsg <: QueueMessage[Y],
//XR <: QueueReader[X, XMsg], XW <: QueueWriter[X],
//YR <: QueueReader[Y, YMsg], YW <: QueueWriter[Y],
//XQueue <: Queue.of[X, Ctx, XMsg, XR, XW], YQueue <: Queue.of[Y, Ctx, YMsg, YR, YW]](
//                                                                                 val xQueue: XQueue,
//                                                                                 val yQueue: YQueue,
//                                                                                 val xMonoid: Monoid[X],
//                                                                                 val yMonoid: Monoid[Y])
//  extends Queue[(X, Y), Ctx](xQueue.name + "_" + yQueue.name) { productQueue =>
//
//
//  override type Msg = ProductMessage[X, Y, Ctx, XMsg, YMsg, XR, XW, YR, YW, XQueue, YQueue]
//
//  override type Writer = ProductQueueWriter[X, Y, Ctx, XMsg, YMsg, XR, XW, YR, YW, XQueue, YQueue]
//  override type Reader = ProductQueueReader[X, Y, Ctx, XMsg, YMsg, XR, XW, YR, YW, XQueue, YQueue]
//
//  override def create(ctx: Context): Try[QueueOp[(X, Y), Msg, Reader, Writer]] = {
//    xQueue.create(ctx).flatMap { xQueueOp =>
//      yQueue.create(ctx).map( yQueueOp =>
//        new ProductQueueOp(productQueue, xQueueOp, yQueueOp)
//      )
//    }
//  }
//
//
//}
//
//
//
//class ProductQueueReader[X, Y, Ctx, XMsg <: QueueMessage[X], YMsg <: QueueMessage[Y],
//XR <: QueueReader[X, XMsg], XW <: QueueWriter[X],
//YR <: QueueReader[Y, YMsg], YW <: QueueWriter[Y],
//XQueue <: Queue.of[X, Ctx, XMsg, XR, XW], YQueue <: Queue.of[Y, Ctx, YMsg, YR, YW]]
//                        (val queueOp: ProductQueueOp[X, Y, Ctx, XMsg, YMsg, XR, XW, YR, YW, XQueue, YQueue]) extends QueueReader[(X, Y), ProductMessage[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue]] {
//
//
//  override def receiveMessage(logger: Logger): Try[Option[ProductMessage[X, Y, Ctx, XMsg, YMsg, XR, XW, YR, YW, XQueue, YQueue]]] = {
//
//  }
//}
//
//class ProductQueueWriter[X, Y, Ctx, XMsg <: QueueMessage[X], YMsg <: QueueMessage[Y],
//XR <: QueueReader[X, XMsg], XW <: QueueWriter[X],
//YR <: QueueReader[Y, YMsg], YW <: QueueWriter[Y],
//XQueue <: Queue.of[X, Ctx, XMsg, XR, XW], YQueue <: Queue.of[Y, Ctx, YMsg, YR, YW]]
//(productQueueOp: ProductQueueOp[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue]) extends QueueWriter[(X, Y), ProductMessage[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue]] {
//  //def write(originId: String, writer: String, values: List[Element]): Try[Unit]
//  override def writeRaw(values: List[(String, (X, Y))]): Try[Unit] = ???
//}
//
//class ProductQueueOp[X, Y, Ctx, XMsg <: QueueMessage[X], YMsg <: QueueMessage[Y],
//XR <: QueueReader[X, XMsg], XW <: QueueWriter[X],
//YR <: QueueReader[Y, YMsg], YW <: QueueWriter[Y],
//XQueue <: Queue.of[X, Ctx, XMsg, XR, XW], YQueue <: Queue.of[Y, Ctx, YMsg, YR, YW]]
//(val queue: ProductQueue[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue], xQueueOp: QueueOp[]) extends QueueOp[
//  (X, Y), ProductMessage[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue],
//  ProductQueueReader[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue],
//  ProductQueueWriter[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue]] {
//
//  override def deleteMessage(message: ProductMessage[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue]): Try[Unit] = ???
//
//  override def writer: Try[ProductQueueWriter[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue]] = ???
//
//  override def reader: Try[ProductQueueReader[X, Y, Ctx, XMsg, YMsg, XQueue, YQueue]] = ???
//
//  override def size: Try[Int] = ???
//
//  override def delete(): Try[Unit] = ???
//
//  override def get(key: String): Try[(X, Y)] = ???
//
//  override def list(lastKey: Option[String], limit: Option[Int]): Try[(Option[String], List[String])] = ???
//
//  override def isEmpty: Try[Boolean] = ???
//
//  override val queue: AnyQueue = _
//}


//case class ProductQueue[X, Y](xQueue: Queue[X], yQueue: Queue[Y])
//  extends Queue[(X, Y)](xQueue.name + "_" + yQueue.name) {
//
//  class ProductMessage(val m1: xQueue.Message, val m2: yQueue.Message) extends QueueMessage[(X, Y)] {
//    override def getBody: Try[(X, Y)] = Try{ m1.getBody.get -> m2.getBody.get }
//
//    override def getId: Try[String] = Try{ m1.getId.get + "," + m2.getId.get }
//  }
//
//  override type Message = ProductMessage
//
//
//
//  override type Context = this.type
//
//  override def create(ctx: Context): QueueOp[(X, Y), Message] = ???
//
//  class ProductQueueOp
//
//  override def deleteMessage(message: ProductMessage): Try[Unit] = Try {
//    xQueue.deleteMessage(message.m1).get
//    yQueue.deleteMessage(message.m2).get
//  }
//  override type QW = ProductQueueWriter
//  override type QR = UnitReader.type
//
//  override def getWriter: Try[QW] = Try{new ProductQueueWriter(xQueue.getWriter.get, yQueue.getWriter.get)}
//
//  override def getReader: Try[QR] = UnitReader.getMessage
//
//
//  override def isEmpty: Boolean = xQueue.isEmpty && yQueue.isEmpty
//
//  override def delete(): Try[Unit] = Success(())
//
//
//  //todo fix name
//  class ProductQueueWriter(xWriter: xQueue.QW, yWriter: yQueue.QW) extends QueueWriter[(X, Y), ProductMessage] {
//    override def write(prefixId: String, values: List[(X, Y)]) = {
//      val l1  = values.zipWithIndex.map { case (value, i) =>
//        (prefixId + "._1." + (i+1), value._1)
//      }
//      val l2  = values.zipWithIndex.map { case (value, i) =>
//        (prefixId + "._1." + (i+1), value._2)
//      }
//      Try {
//        xWriter.writeRaw(l1).get
//        yWriter.writeRaw(l2).get
//      }
//    }
//
//    def writeRaw(values: List[(String, (X, Y))]) = Failure(new Error("not implemented"))
//
//
//
//  }
//
//  object UnitReader extends QueueReader[(X, Y), ProductMessage] {
//    override def getMessage = Failure(new Error("can't read from ProductQueue"))
//  }
//
//
//}
//
//
//
//  object ProductQueue {
//  def flatQueue(queue: QueueAux): List[QueueAux] = {
//    queue match {
//      case ProductQueue(q1, q2) => flatQueue(q1) ++ flatQueue(q2)
//      case q => List(q)
//    }
//  }
//}