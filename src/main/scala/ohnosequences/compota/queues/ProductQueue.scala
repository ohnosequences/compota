package ohnosequences.compota.queues


import ohnosequences.compota.monoid.Monoid
import ohnosequences.logging.Logger

import scala.util.{Success, Try, Failure}


trait AnyProductMessage extends AnyQueueMessage {
  type XElement
  type YElement

  override type QueueMessageElement = (XElement, YElement)

  type XMessage <: AnyQueueMessage.of[XElement]
  type YMessage <: AnyQueueMessage.of[YElement]

  val message: Either[XMessage, YMessage]

  val xMonoid: Monoid[XElement]
  val yMonoid: Monoid[YElement]

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


//todo make it internal
case class ProductMessage[X, Y, XM <: AnyQueueMessage.of[X], YM <: AnyQueueMessage.of[Y]](message: Either[XM, YM],
                                                                                              xMonoid: Monoid[X],
                                                                                              yMonoid: Monoid[Y]) extends AnyProductMessage {
  override type XElement = X
  override type YElement = Y
  override type XMessage = XM
  override type YMessage = YM
}


case class ProductQueueReader[X, Y, XM <: AnyQueueMessage.of[X], YM <: AnyQueueMessage.of[Y]](queueOp: AnyProductQueueOp.of4[X, Y, XM, YM],
                                                                                                  xReader: AnyQueueReader.of[X, XM],
                                                                                                  yReader: AnyQueueReader.of[Y, YM]
                                                                                                  ) extends AnyQueueReader {

  override type QueueReaderElement = (X, Y)
  override type QueueReaderMessage = ProductMessage[X, Y, XM, YM]

  override def receiveMessage(logger: Logger): Try[Option[QueueReaderMessage]] = {
    if (scala.util.Random.nextBoolean()) {
      xReader.receiveMessage(logger).map {
        case None => None
        case Some(xmsg) => Some(ProductMessage(Left(xmsg), queueOp.queue.xMonoid, queueOp.queue.yMonoid))
      }
    } else {
      yReader.receiveMessage(logger).map {
        case None => None
        case Some(ymsg) => Some(ProductMessage(Right(ymsg), queueOp.queue.xMonoid, queueOp.queue.yMonoid))
      }
    }
  }
}

case class ProductQueueWriter[X, Y](queueOp: AnyProductQueueOp.of2[X, Y], xWriter: AnyQueueWriter.of[X], yWriter: AnyQueueWriter.of[Y]) extends AnyQueueWriter {
  override type QueueWriterElement =  (X, Y)

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


trait AnyProductQueueOp extends AnyQueueOp { productQueueOp =>

  type XElement
  type YElement

  override type QueueOpElement = (XElement, YElement)

  type XMessage <: AnyQueueMessage.of[XElement]
  type YMessage <: AnyQueueMessage.of[YElement]

  val queue: AnyProductQueue.of4[XElement, YElement, XMessage, YMessage, QueueOpQueueContext]

  val xQueueOp: AnyQueueOp.of2[XElement, XMessage]
  val yQueueOp: AnyQueueOp.of2[YElement, YMessage]

  override def subOps(): List[AnyQueueOp] = xQueueOp.subOps() ++ yQueueOp.subOps()

  override type QueueOpQueueMessage = ProductMessage[XElement, YElement, XMessage, YMessage]

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
        (queue.xMonoid.unit, yEl)
      }
      case Success(xEl) =>  Success((xEl, queue.yMonoid.unit))
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

  override type QueueOpQueueWriter = ProductQueueWriter[XElement, YElement]

  override type QueueOpQueueReader = ProductQueueReader[XElement, YElement, XMessage, YMessage]
}

object AnyProductQueueOp {
  type of4[X, Y, XM <: AnyQueueMessage.of[X], YM <: AnyQueueMessage.of[Y]] = AnyProductQueueOp {
    type XElement = X
    type YElement = Y
    type XMessage = XM
    type YMessage = YM
  }

  type of2[X, Y] = AnyProductQueueOp {
    type XElement = X
    type YElement = Y
  }
}

case class ProductQueueOp[X, Y, XM <: AnyQueueMessage.of[X], YM <: AnyQueueMessage.of[Y], C](
                                                                                           context: C,
                                                                                           queue: AnyProductQueue.of4[X, Y, XM, YM, C],
                                                                                           xQueueOp: AnyQueueOp.of2[X, XM],
                                                                                           yQueueOp: AnyQueueOp.of2[Y, YM]) extends AnyProductQueueOp {
  override type XElement = X
  override type YElement = Y
  override type XMessage = XM
  override type YMessage = YM
  override type QueueOpQueueContext = C
}


trait AnyProductQueue extends AnyQueue { anyProductQueue =>
  type XElement
  type YElement

  val xMonoid: Monoid[XElement]
  val yMonoid: Monoid[YElement]

  type XMessage <: AnyQueueMessage.of[XElement]
  type YMessage <: AnyQueueMessage.of[YElement]

  val xQueue: AnyQueue.of3[XElement, QueueContext, XMessage]
  val yQueue: AnyQueue.of3[YElement, QueueContext, YMessage]

  override type QueueElement = (XElement, YElement)

  override type QueueQueueMessage = ProductMessage[XElement, YElement, XMessage, YMessage]

  override type QueueQueueReader = ProductQueueReader[XElement, YElement, XMessage, YMessage]
  override type QueueQueueWriter = ProductQueueWriter[XElement, YElement]
  override type QueueQueueOp = ProductQueueOp[XElement, YElement, XMessage, YMessage, QueueContext]

  override def subQueues: List[AnyQueue] = xQueue.subQueues ++ yQueue.subQueues

  override def create(ctx: QueueContext): Try[QueueQueueOp] = {

    xQueue.create(ctx).flatMap { xQueueOp =>
      yQueue.create(ctx).map { yQueueOp =>
        ProductQueueOp(ctx, anyProductQueue, xQueueOp, yQueueOp)
      }
    }
  }
}

object AnyProductQueue {
  type of4[X, Y, XM <: AnyQueueMessage.of[X], YM <: AnyQueueMessage.of[Y], C] = AnyProductQueue {
    type XElement = X
    type YElement = Y
    type XMessage = XM
    type YMessage = YM
    type QueueContext = C
  }
}

class ProductQueue[
  X,
  Y,
  XM <: AnyQueueMessage.of[X],
  YM <: AnyQueueMessage.of[Y],
  Ctx](val xQueue: AnyQueue.of3[X, Ctx, XM], val yQueue: AnyQueue.of3[Y, Ctx, YM], val xMonoid: Monoid[X], val yMonoid: Monoid[Y]) extends AnyProductQueue {

  override type XElement = X
  override type YElement = Y


  val name = xQueue.name + "_" + yQueue.name

  override type QueueContext = Ctx

  override type XMessage = XM
  override type YMessage = YM
}

