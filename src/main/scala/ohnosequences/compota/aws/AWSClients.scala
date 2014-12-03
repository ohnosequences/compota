package ohnosequences.compota.aws

import com.amazonaws.auth.{AWSCredentialsProvider}
import ohnosequences.awstools.ec2.EC2
import ohnosequences.awstools.autoscaling.AutoScaling
import ohnosequences.awstools.sqs.SQS
import ohnosequences.awstools.sns.SNS
import ohnosequences.awstools.s3.S3
import ohnosequences.awstools.regions.Region.Ireland
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient

trait AWSClients {
  val ec2: EC2
  val as: AutoScaling
  val sqs: SQS
  val sns: SNS
  val s3: S3
  val ddb: AmazonDynamoDBClient
}

object AWSClients {
  def create(credentialsProvider: AWSCredentialsProvider, region: ohnosequences.awstools.regions.Region = Ireland) = new AWSClients {
    val ec2 = EC2.create(credentialsProvider, region)
    val as = AutoScaling.create(credentialsProvider, ec2, region)
    val sqs = SQS.create(credentialsProvider, region)
    val sns = SNS.create(credentialsProvider, region)
    val s3 = S3.create(credentialsProvider, region)
    val ddb = new AmazonDynamoDBClient(credentialsProvider)
    ddb.setRegion(region)
  }
}

//class AWS(credentialsFile: File, region: ohnosequences.awstools.regions.Region = Ireland) {
//  val AWS_ACCESS_KEY = "AWS_ACCESS_KEY"
//  val AWS_SECRET_KEY = "AWS_SECRET_KEY"
//
//  val env: Option[(String, String)] = try {
//    Some(System.getenv(AWS_ACCESS_KEY) -> System.getenv(AWS_SECRET_KEY))
//  } catch {
//    case t: Throwable => None
//  }
//
//  val credentialsProvider = env match {
//    case Some((accessKey: String, secretKey: String)) => {
//      new StaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey))
//    }
//    case _ => {
//      if (credentialsFile.exists()) {
//        new StaticCredentialsProvider(new PropertiesCredentials(credentialsFile))
//      } else {
//        // println("why I'm here: " + env)
//        new InstanceProfileCredentialsProvider()
//      }
//    }
//  }
//
//
//}