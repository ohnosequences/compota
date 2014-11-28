//package ohnosequences.compota.aws.queues
//
//import ohnosequences.compota.Fabric
//import ohnosequences.compota.aws.AWS
//import ohnosequences.compota.queues.{QueueMessage, Queue}
//import ohnosequences.compota.queues.local.SimpleMessage
//
//import scala.util.{Success, Try}
//
//case class SQSMessage[E](id: String, body: E, handle: String) extends QueueMessage[E] {
//  override def getBody = Success(body)
//
//
//
//  override def getId: Try[String] = Success(id)
//}
//
//
//
//
//abstract class DynamoDBQueueConfig[T](name: String) extends Fabric[AWS, Queue[T]] {
//
//
//  abstract class DynamoDBQueue(name: String, sqsUrl: String, aws: AWS) extends Queue[T](name) {
//    //  aws.sqs.sqs.deleteMessage()
//
//    override type Message = SQSMessage[T]
//
//    override def deleteMessage(message: Message): Try[Unit] = Try{
//      aws.sqs.sqs.deleteMessage(sqsUrl, message.handle)
//    }
//
//    override def delete(): Try[Unit] = ???
//
//    override def getWriter: Try[QW] = ???
//
//    override def isEmpty: Boolean = ???
//
//    override def getReader: Try[QR] = ???
//
//    override type QW = this.type
//    override type QR = this.type
//  }
//
//  override def build(ctx: AWS): DynamoDBQueue[T] = {
//    new DynamoDBQueue[T](name, "url", ctx) {}
//  }
//}
//
//trait