package ohnosequences.compota.aws

import ohnosequences.compota.Namespace
import ohnosequences.compota.environment.InstanceId
import ohnosequences.logging.ConsoleLogger
import org.junit.Test
import org.junit.Assert._

import scala.util.{Random, Failure}

class ErrorTableTest {
 // @Test
  def testCount() {
    val tableName = "errorTable.testCount"
    TestCredentials.aws match {
      case None => println("this test requires test credentials")
      case Some(aws) => {
        val logger = new ConsoleLogger(prefix = "errorTableTest()", debug = true)
        aws.ddb.deleteTable(tableName)
        AwsErrorTable.apply(logger, tableName, aws).recoverWith { case t=>
          val error = new Error("failed to create error table", t)
          logger.error(error)
          fail(error.toString)
          Failure(error)
        }.foreach { table =>
          val namespace = Namespace("test") / "errorTable"
          val count = 50
          for (i <- 1 to count) {
            val testError = new Error("test error " + i)
            val instance = InstanceId("test_instance_" + Random.nextInt(10))
            val errorMessage = new StringBuilder()
            logger.printThrowable(testError, {s => errorMessage.append(s + System.lineSeparator())})
            logger.debug("reporting error " + testError + " to error table")
            table.reportError(namespace, System.currentTimeMillis(),  instance, errorMessage.toString(), "stackTrace").get
          }
          assertEquals(count, table.getNamespaceErrorCount(namespace).get)
        }
      }
    }
  }
}
