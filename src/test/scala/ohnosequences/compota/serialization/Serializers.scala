package ohnosequences.compota.serialization

import org.scalacheck.{Gen, Properties}
import org.scalacheck.Prop._

object Serializers extends Properties("Serializers") {
  property("stringSerializer1") = forAll(Gen.alphaStr) { s => stringSerializer.fromString(s).get.equals(s)}
  property("intSerializer") = forAll(Gen.choose(Int.MinValue, Int.MaxValue)) { n =>
    intSerializer.fromString(n.toString).get.equals(n)}
}
