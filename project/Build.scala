import sbt._
import Keys._
import com.amazonaws.auth._
import ohnosequences.sbt.nice.ResolverSettings._
import sbtassembly._
import AssemblyKeys._

object CompotaBuild extends Build {

  val testCredentialsProvider = SettingKey[Option[AWSCredentialsProvider]]("credentials provider for test environment")

 // val testMainClass = SettingKey[Option[String]]("compota main class")

  override lazy val settings = super.settings ++ Seq(testCredentialsProvider := None)

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

    //todo fix!
    case p => ""
  }

  def optionStringPrinter(o: Option[String]): String = o match {
    case None => "None"
    case Some(s) => "Some(\"" + s + "\")"
  }



  lazy val root = Project(id = "compota",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      sourceGenerators in Test += task[Seq[File]] {
        val text = """
                     |package ohnosequences.compota.test.generated
                     |
                     |object credentials {
                     |  val credentialsProvider: Option[com.amazonaws.auth.AWSCredentialsProvider] = $cred$
                     |}
                     |""".stripMargin
          .replace("$cred$", testCredentialsProvider.value.map(providerConstructorPrinter).toString)
        val file = (sourceManaged in Compile).value / "testCredentials.scala"
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
                   |
                   |object metadata extends ohnosequences.compota.aws.deployment.AnyMetadata {
                   |  override val artifact = "$artifact$"
                   |  override val jarUrl = "$jarUrl$"
                   |  override val testJarUrl = Some("$testJarUrl$")
                   |  override val mainClass = $mainClass$
                   |}
                   |""".stripMargin
        .replace("$artifact$", (name.value + version.value).toLowerCase)
        .replace("$jarUrl$", fatJarUrl)
        .replace("$testJarUrl$", testJarUrl)
        .replace("$mainClass$", optionStringPrinter(None))
      val file = (sourceManaged in Compile).value / "testMetadata.scala"
      IO.write(file, text)
      Seq(file)
    }
    )
  )
}