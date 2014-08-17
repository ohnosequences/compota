package ohnosequences.nisperon.queues

import com.typesafe.scalalogging.LazyLogging
import ohnosequences.awstools.s3.ObjectAddress
import ohnosequences.nisperon.{Nisperon, Serializer}

//todo write to other queue!!! ??
//todo use previous results!!
object Merger {
  def mergeDestination(nisperon: Nisperon, queue: MonoidQueueAux): ObjectAddress = {
    ObjectAddress(nisperon.nisperonConfiguration.bucket, "results/" + queue.name)
  }
}

class Merger(queue: MonoidQueueAux, nisperon: Nisperon) extends LazyLogging {


  def merge() = {

   // queue.initRead()

    logger.info("retrieving messages from the queue " + queue.name)
    val ids = queue.list()
    var res = queue.monoid.unit

    var left = ids.size

    logger.info("merging " + left + " messages")
    ids.foreach { id =>
      left -= 1
      queue.read(id) match {
        case None => logger.error("message " + id + " not found")
        case Some(m) => res = queue.monoid.mult(res, m)
      }

      if (left % 100 == 0) {
         logger.info(left + " messages left")
      }

    }

    logger.info("merged")

    logger.info("writing result")


    val result = Merger.mergeDestination(nisperon, queue)
    nisperon.aws.s3.putWholeObject(result, queue.serializer.toString(res))
    //queue.put("result", List(res))

//    //todo do it optional
//    logger.info("deleting messages")
//
//
//    left = ids.size
//    ids.foreach { id =>
//      left -= 1
//      if (left % 100 == 0) {
//        logger.info(left + " messages left")
//      }
//      try {
//        queue.delete(id)
//      } catch {
//        case t: Throwable => logger.error("error during deleting message " + id)
//      }
//    }
   // queue.reset()

  }


}
