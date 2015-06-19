package ohnosequences.compota.test.aws

import com.amazonaws.auth.AWSCredentialsProvider
import ohnosequences.awstools.AWSClients
import ohnosequences.compota.aws.deployment.{Metadata, AnyMetadata}
import ohnosequences.logging.{ConsoleLogger, Logger}

import scala.util.{Try, Success, Failure}

object AwsCompotaTest {
  val aws: Option[AWSClients] = {
    ohnosequences.compota.test.generated.credentialsProvider.credentialsProvider.map (AWSClients.create(_))
  }

  val testMetadata: Metadata = ohnosequences.compota.test.generated.metadata.metadata
  val testCredentialsProvider: AWSCredentialsProvider = ohnosequences.compota.test.generated.credentialsProvider.credentialsProvider
  val testNotificationEmail: String = ohnosequences.compota.test.generated.email.email
}

trait AwsCompotaTest {



//  def test[T](name: String, debug: Boolean = false)(action: (AWSCredentialsProvider, String) => Try[T]): Unit = {
//    val logger = new ConsoleLogger(name, debug)
//    (
//      ohnosequences.compota.test.generated.awsCredentials.credentialsProvider,
//      ohnosequences.compota.test.generated.email.email
//    ) match {
//      case (Some(provider), Some(email)) => {
//        action(provider, email).recoverWith { case t =>
//          logger.error(t)
//          fail(t.toString)
//        }
//      }
//      case _ => logger.warn("aws credentials and a notification email should be defined for this test")
//    }
//  }
}
