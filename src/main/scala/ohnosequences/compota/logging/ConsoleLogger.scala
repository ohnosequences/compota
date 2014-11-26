package ohnosequences.compota.logging

class ConsoleLogger extends Logger {
  def info(s: String) {println(s)}
  def error(s: String) {println(s)}
  def error(t: Throwable) { println("an error occurred:"); t.printStackTrace()}

}
