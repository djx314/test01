package net.scalax.test01

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object ClientRequest {
  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://127.0.0.1:8080/hello"))

    responseFuture
      .andThen {
        case Success(res) => println(res)
        case Failure(_) => sys.error("something wrong")
      }
      .andThen {
        case _ =>
          system.terminate
      }
  }
}