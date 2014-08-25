package ohnosequences.nisperon.logging

trait Logger {
  def warn(s: String)

  def error(s: String)

  def info(s: String)
}


class ConsoleLogger(prefix: String) extends Logger {
  override def info(s: String): Unit = println("[" + "INFO " + prefix + "]: " + s)

  override def error(s: String): Unit = println("[" + "ERROR " + prefix + "]: " + s)

  override def warn(s: String): Unit = println("[" + "WARN " + prefix + "]: " + s)
}