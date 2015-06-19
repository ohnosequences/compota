import sbt._
import Keys._
import com.amazonaws.auth._
import ohnosequences.sbt.nice.ResolverSettings._
import sbtassembly._
import AssemblyKeys._

object CompotaBuild extends Build {

  val testCredentialsProvider = SettingKey[AWSCredentialsProvider]("aws credentials provider for test environment")

  val testNotificationEmail = SettingKey[String]("e-mail address for test notifications")

  override lazy val settings = super.settings ++ Seq(testCredentialsProvider := new InstanceProfileCredentialsProvider(), testNotificationEmail := "")

  def stringOptionPrinter(option: Option[String]): String = option match {
    case None => "None"
    case Some(s) => "Some(\"" + s + "\")"
  }

  def artifactPrepare(s: String): String = {
    s.split("\\W").reduce(_ + "_" + _)
  }

  def providerConstructorPrinter(provider: AWSCredentialsProvider) = provider match {
    case ip: InstanceProfileCredentialsProvider => {
      "new com.amazonaws.auth.InstanceProfileCredentialsProvider()"
    }
    case ep: EnvironmentVariableCredentialsProvider => {
      "new com.amazonaws.auth.EnvironmentVariableCredentialsProvider()"
    }
    case pp: PropertiesFileCredentialsProvider => {
      val field = pp.getClass().getDeclaredField("credentialsFilePath")
      field.setAccessible(true)
      val path = field.get(pp).toString
      "new com.amazonaws.auth.PropertiesFileCredentialsProvider(\"\"\"$path$\"\"\")".replace("$path$", path)
    }
    case p => "new com.amazonaws.auth.InstanceProfileCredentialsProvider()"
  }

  lazy val root = Project(id = "compota",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      sourceGenerators in Test += task[Seq[File]] {
        val text = """
                     |package ohnosequences.compota.test.generated
                     |
                     |object awsCredentials {
                     |  val credentialsProvider: com.amazonaws.auth.AWSCredentialsProvider = $cred$
                     |}
                     |""".stripMargin
          .replace("$cred$", providerConstructorPrinter(testCredentialsProvider.value))
        val file = (sourceManaged in Compile).value / "awsCredentials.scala"
        IO.write(file, text)
        Seq(file)
      },
      sourceGenerators in Test += task[Seq[File]] {
        val text = """
                     |package ohnosequences.compota.test.generated
                     |
                     |object email {
                     |  val email: String = $email$
                     |}
                     |""".stripMargin
          .replace("$email$", "\"" + testNotificationEmail.value + "\"")
        val file = (sourceManaged in Compile).value / "email.scala"
        IO.write(file, text)
        Seq(file)
      },
      sourceGenerators in Test += task[Seq[File]] {
        val fatJarUrl = {
          val isMvn = publishMavenStyle.value
          val scalaV = "_"+scalaBinaryVersion.value
          val module = moduleName.value + scalaV
          val artifact =
            (if (isMvn) "" else "jars/") +
              module +
              (if (isMvn) "-"+version.value else "") +
              "-fat.jar"
          Seq( publishS3Resolver.value.url
            , organization.value
            , module
            , version.value
            , artifact
          ).mkString("/")
        }
        val testJarUrl = {
          val isMvn = publishMavenStyle.value
          val scalaV = "_"+scalaBinaryVersion.value
          val module = moduleName.value + scalaV
          val artifact =
            (if (isMvn) "" else "jars/") +
              module +
              (if (isMvn) "-"+version.value else "") +
              "-test.jar"
          Seq( publishS3Resolver.value.url
            , organization.value
            , module
            , version.value
            , artifact
          ).mkString("/")
        }
      val text = """
                   |package ohnosequences.compota.test.generated
                   |import ohnosequences.compota.aws.deployment.Metadata
                   |
                   |object metadata {
                   |  val metadata: Metadata = Metadata(
                   |    artifact = "$artifact$",
                   |    jarUrl = "$jarUrl$",
                   |    testJarUrl = Some("$testJarUrl$"),
                   |    mainClass = $mainClass$
                   |  )
                   |}
                   |""".stripMargin
        .replace("$artifact$", artifactPrepare(name.value + version.value))
        .replace("$jarUrl$", fatJarUrl)
        .replace("$testJarUrl$", testJarUrl)
        .replace("$mainClass$", stringOptionPrinter(None))
      val file = (sourceManaged in Compile).value / "metadata.scala"
      IO.write(file, text)
      Seq(file)
    }
    )
  )
}