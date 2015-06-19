package ohnosequences.compota.console

/**
 * Created by Evdokim on 16.06.2015.
 */
object Pagination {
  def listPagination[T](list: List[T], limit: Option[Int], lastToken: Option[String]): (Option[String], List[T]) = {
    def listPagination0(list: List[T], offset: Int): (Option[String], List[T]) = limit match {
      case None => (Some((offset + list.size).toString), list)
      case Some(l) if l < list.size => (Some((offset + l).toString), list.take(l))
      case Some(l) => (Some((offset + list.size).toString), list)
    }
    lastToken match {
      case None => listPagination0(list, 0)
      case Some(l) => listPagination0(list.drop(l.toInt), l.toInt)
    }
  }
}
