package ohnosequences.compota.console

import java.io.ByteArrayInputStream

import ohnosequences.logging.Logger
import unfiltered.Cycle
import unfiltered.netty._
import unfiltered.netty.cycle.Plan.Intent
import unfiltered.response._
import unfiltered.request.{BasicAuth, Path, Seg, GET}
import unfiltered.netty.cycle.{Plan, SynchronousExecution}

import scala.util.{Try, Failure, Success}


trait Users {
  def auth(u: String, p: String): Boolean
}

//object Decode {
//  import java.net.URLDecoder
//  import java.nio.charset.Charset
//
//  trait Extract {
//    def charset: Charset
//    def unapply(raw: String) =
//      Try(URLDecoder.decode(raw, charset.name())).toOption
//  }
//
//  object utf8 extends Extract {
//    val charset = Charset.forName("utf8")
//  }
//}

case class AuthWithLogging(users: Users, logger: Logger) {
  def apply[A, B](intent: Cycle.Intent[A, B]) =
    Cycle.Intent[A, B] {
      case req@BasicAuth(user, pass) if users.auth(user, pass) =>
        //println(req.uri)
        logger.info(req.uri)
        Cycle.Intent.complete(intent)(req)
      case unAuth =>
        Unauthorized ~> WWWAuthenticate( """Basic realm="/"""")
    }
}

case class HtmlCustom(s: String) extends ComposeResponse[Any](HtmlContent ~> ResponseString(s))

@io.netty.channel.ChannelHandler.Sharable
class ConsolePlan(users: Users, console: AnyConsole) extends Plan with Secured
with SynchronousExecution
with ServerErrorResponse {
  val logger = console.logger

  def intent = AuthWithLogging(users, logger) {
    case GET(Path("/")) => {
      val mainPage = console.mainHTML.mkString
        .replace("@main", console.compotaInfoPage.toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)
      HtmlCustom(mainPage)
    }

    case GET(Path("/undeploy")) => {
      console.sendForceUnDeployCommand("terminated from console", "terminated from console")
      ResponseString("undeploy message was sent")
    }

    case GET(Path("/errorsPage")) => {

      val mainPage = console.mainHTML.mkString
        .replace("@main", console.errorsPage.toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)

      HtmlCustom(mainPage)
    }


    case GET(Path("/namespacePage")) => {

      val page = console.mainHTML.mkString
        .replace("@main", console.namespacePage.toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)

      HtmlCustom(page)
    }

    case GET(Path(Seg("errors" :: args))) => {
      ResponseString(console.printErrorTable(args.headOption).toString())
    }

    case GET(Path("/shutdown")) => {
      console.shutdown()
      ResponseString("ok")
    }

    case GET(Path(Seg("queue" :: queueName :: "messages" :: args))) => {
      ResponseString(console.printMessages(queueName, args.headOption).toString())
    }

    case GET(Path(Seg("logging" :: "raw" :: instanceId :: namespace))) => {
      console.getLogRaw(instanceId, namespace) match {
        case Success(Left(url)) => Redirect(url.toString)
        case Success(Right(log)) => ResponseString(log)
        case Failure(t) => NotFound
      }
    }

    case GET(Path(Seg("logging" :: instanceId :: namespace))) => {
      ResponseString(console.printLog(instanceId, namespace).toString())
    }

    case GET(Path(Seg("instance" :: "ssh" :: instanceId :: namespace))) => {
      ResponseString(console.sshInstance(instanceId, namespace).toString)
    }

    case GET(Path(Seg("instance" :: "stackTrace" :: id :: namespace))) => {
      ResponseString(console.stackTraceInstance(id, namespace).toString)
    }

    case GET(Path(Seg("instance" :: "terminate" :: id :: namespace))) => {
      ResponseString(console.terminateInstance(id, namespace).toString)
    }

    case GET(Path(Seg("queue" :: queueName :: "message" :: id :: Nil))) => {
      console.getMessage(queueName, id) match {
        case Failure(t) => ResponseString(t.toString)
        case Success(Left(url)) => Redirect(url.toString)
        case Success(Right(log)) => ResponseString(log)
      }
    }

    case GET(Path(Seg("error" :: "message" :: timestamp :: instanceId :: namespace))) => {
      console.getErrorMessage(instanceId, namespace, timestamp) match {
        case Success(s) => ResponseString(s)
        case Failure(t) => NotFound
      }
    }

    case GET(Path(Seg("error" :: "stackTrace" :: timestamp :: instanceId :: namespace))) => {
      console.getErrorStackTrace(instanceId, namespace, timestamp) match {
        case Success(s) => ResponseString(s)
        case Failure(t) => NotFound
      }
    }

    case GET(Path(Seg("nispero" :: nispero :: "workers" :: args))) => {
      ResponseString(console.printWorkers(nispero, args.headOption).toString())
    }

    case GET(Path(Seg("metamanagers" :: args))) => {
      ResponseString(console.printManagers(args.headOption).toString())
    }


    case GET(Path(Seg("namespaces" :: args))) => {
      ResponseString(console.printNamespaces(args.headOption).toString())
    }

    case GET(Path("/threads")) => {
      val resp = new StringBuilder

      import scala.collection.JavaConversions._

      Thread.getAllStackTraces.foreach { case (thread, sts) =>
        resp.append(thread + ":" + thread.getState + System.lineSeparator())
        sts.foreach { st =>
          resp.append("      " + st.toString + System.lineSeparator())
        }
      }
      ResponseString(resp.toString)

    }

    case GET(Path(Seg("nispero" :: nispero :: Nil))) => {

      val mainPage = console.mainHTML.mkString
        .replace("@main", console.nisperoInfoPage(nispero).toString())
        .replace("@sidebar", console.sidebar.toString())
        .replace("$name$", console.name)
        .replace("$nispero$", nispero)

      HtmlCustom(mainPage)
    }

    case GET(Path("/main.css")) => {
      // val main = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/console/main.css")).mkString
      CssContent ~> ResponseString(console.mainCSS)
    }
  }

}


class UnfilteredConsoleServer(console: AnyConsole, currentAddress: String) {


  object users extends Users {
    val userName = "compota"

    override def auth(u: String, p: String): Boolean = userName.equals(u) && p.equals(console.password)
  }


  def shutdown() {
    Runtime.getRuntime().halt(0)
  }

  //  def currentAddress: String = {
  //    aws.ec2.getCurrentInstance.flatMap {_.getPublicDNS()}.getOrElse("<undefined>")
  //  }

  def printURL(domain: String): String = console.isHttps match {
    case true => "https://" + domain
    case false => "http://" + domain
  }

  def startedMessage(customInfo: String): String = {
    val message = new StringBuilder()
    message.append("console address: " + printURL(currentAddress) + System.lineSeparator())
    message.append("user: " + users.userName + System.lineSeparator())
    message.append("password: " + console.password + System.lineSeparator())
    if (!customInfo.isEmpty) {
      message.append(System.lineSeparator())
      message.append(System.lineSeparator())
      message.append(customInfo)
    }
    message.toString()
  }


  def start(): Try[UnfilteredConsoleServer] = {
    Try {
      console.logger.info("starting console server")
      val server: Server = if (console.isHttps) {
        unfiltered.netty.Server.https(port = console.port, ssl = SslContextProvider.selfSigned(new io.netty.handler.ssl.util.SelfSignedCertificate)).handler(new ConsolePlan(users, console))
      } else {
        unfiltered.netty.Server.http(port = console.port).handler(new ConsolePlan(users, console))
      }
      //Starts server in the background
      server.start()
      server
    }.recoverWith { case t =>
      console.logger.warn("binding to default host failed, trying to bind to localhost")
      Try {
        val server = if (console.isHttps) {
          unfiltered.netty.Server.https(port = console.port, "localhost", ssl = SslContextProvider.selfSigned(new io.netty.handler.ssl.util.SelfSignedCertificate)).handler(new ConsolePlan(users, console))
        } else {
          unfiltered.netty.Server.http(port = console.port, "localhost").handler(new ConsolePlan(users, console))
        }
        //Starts server in the background
        server.start()
        server
      }
    } match {
      case Failure(t) => Failure(new Error("failed to start console server", t))
      case Success(s) => {
        console.logger.info("console server started on port " + s.ports.head)
        Success(UnfilteredConsoleServer.this)
      }
    }
  }
}
