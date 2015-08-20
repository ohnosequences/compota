package ohnosequences.compota

import java.io.File

import ohnosequences.awstools.autoscaling.AutoScaling
import ohnosequences.awstools.ec2.EC2
import ohnosequences.awstools.regions.Region.Ireland
import ohnosequences.awstools.s3.S3
import ohnosequences.awstools.sns.SNS
import ohnosequences.awstools.sqs.SQS

import com.amazonaws.auth.{BasicAWSCredentials, InstanceProfileCredentialsProvider, PropertiesCredentials}
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient


class AWS(credentialsFile: File, region: ohnosequences.awstools.regions.Region = Ireland) {
  val AWS_ACCESS_KEY = "AWS_ACCESS_KEY"
  val AWS_SECRET_KEY = "AWS_SECRET_KEY"

  val env: Option[(String, String)] = try {
    Some(System.getenv(AWS_ACCESS_KEY) -> System.getenv(AWS_SECRET_KEY))
  } catch {
    case t: Throwable => None
  }

  val credentialsProvider = env match {
    case Some((accessKey: String, secretKey: String)) => {
      new StaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey))
    }
    case _ => {
      if (credentialsFile.exists()) {
        new StaticCredentialsProvider(new PropertiesCredentials(credentialsFile))
      } else {
        new InstanceProfileCredentialsProvider()
      }
    }
  }

  val ec2 = EC2.create(credentialsProvider, region)
  val as = AutoScaling.create(credentialsProvider, ec2, region)
  val sqs = SQS.create(credentialsProvider, region)
  val sns = SNS.create(credentialsProvider, region)
  val s3 = S3.create(credentialsProvider, region)

  val ddb = new AmazonDynamoDBClient(credentialsProvider)
  ddb.setRegion(com.amazonaws.regions.Region.getRegion(region))
}


