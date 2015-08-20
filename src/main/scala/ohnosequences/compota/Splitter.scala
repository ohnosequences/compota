package ohnosequences.compota

trait Splitter[T] {
  def split(t: T): List[T]
}
