package ohnosequences.nisperon.logging


trait Logger {
  def warn(s: String)

  def error(s: String)

  def info(s: String)

  def benchExecute[T](description: String)(statement: =>T): T = {
    val t1 = System.currentTimeMillis()
    log
    val t2 = System.currentTimeMillis()
  }
}
