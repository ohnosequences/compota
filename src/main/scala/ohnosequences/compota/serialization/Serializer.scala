package ohnosequences.compota.serialization


import scala.util.{Failure, Success, Try}
import scala.util.parsing.json.JSONObject

trait Serializer[T] {
 // def fromString(s: String, errorMessage: String)
  def fromString(s: String): Try[T]
  def toString(t: T): Try[String]
}


class JsonSerializer[T](implicit mf: scala.reflect.Manifest[T]) extends Serializer[T] {

  def fromString(s: String): Try[T] = {
    JSON.extract[T](s)
  }

  def toString(t: T): Try[String] = {
    JSON.toJSON(t.asInstanceOf[AnyRef])
  }

}

object unitSerializer extends Serializer[Unit] {
  def fromString(s: String) = Success(())
  def toString(t: Unit) = Success("")
}

object intSerializer extends Serializer[Int] {

  def fromString(s: String): Try[Int] = try {
    if(s == null) {
      Failure(new Error("can't convert null to Int"))
    } else {
      Success(s.toInt)
    }
  } catch {
    case b: NumberFormatException => Failure(b)
  }

  def toString(t: Int): Try[String] = Success(t.toString)

}

object stringSerializer extends Serializer[String] {
  def fromString(s: String) = Success(s)

  def toString(t: String) = Success(t)
}

class MapSerializer[K, V](kSerializer: Serializer[K], vSerializer: Serializer[V]) extends Serializer[Map[K, V]] {
  override def toString(t: Map[K, V]) = Try {

    val rawMap: Map[String, String] = t.map { case (key, value) =>
      (kSerializer.toString(key).get, vSerializer.toString(value).get)
    }

    JSONObject(rawMap).toString()
  }


  override def fromString(s: String) = Try {
    scala.util.parsing.json.JSON.parseFull(s) match {
      case Some(map: Map[String, String]) => {
        map.map {
          case (key, value) =>
            (kSerializer.fromString(key).get, vSerializer.fromString(value).get)
        }
      }
    }
  }
}

