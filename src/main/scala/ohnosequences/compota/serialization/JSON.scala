package ohnosequences.compota.serialization

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{writePretty}

import scala.util.{Failure, Success, Try}

object JSON {
  implicit val formats = DefaultFormats

  def extract[T](json: String)(implicit mf: scala.reflect.Manifest[T]): Try[T] = {
    try {
      Success(parse(json).extract[T])
    } catch {
      case t: Throwable => Failure(t)
    }
  }

  def toJSON(a: AnyRef): Try[String] = {
    try {
      Success(writePretty(a))
    } catch {
      case t: Throwable => Failure(t)
    }
  }

}
