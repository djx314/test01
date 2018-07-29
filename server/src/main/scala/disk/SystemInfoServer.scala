package net.scalax.test01

import java.nio.file.{ Files, Paths }
import java.text.SimpleDateFormat
import java.util.{ Date, Timer, TimerTask }

import akka.Done
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.stream.scaladsl.{ FileIO, Source }
import akka.util.ByteString

import scala.concurrent.{ Future, Promise }
import scala.io.StdIn
import scala.util.{ Failure, Success }

object SystemInfoServer {
  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route = path("hello") {
      get {
        complete(HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
              <html>
                <head>
                </head>
                <body>
                  <form method="post" action="./userInfo" enctype="multipart/form-data">
                  <table>
                    <tr>
                      <td>姓名</td>
                      <td><input name="user_name" /></td>
              </tr>
              <tr>
                <td>学号</td>
                <td><input name="user_num" /></td>
              </tr>
                <tr>
                  <td>文件</td>
                  <td><input name="user_file" type="file" /></td>
                </tr>
                <tr>
                  <td cols="2"><input type="submit" value="提交" /></td>
                </tr>
              </table>
              </form>
              </body>
              </html>
              """.stripMargin))
      }
    } ~
      path("userInfo") {
        post {
          extractMaterializer { implicit mat =>
            import scala.concurrent.duration._
            toStrictEntity(3.seconds) {
              formFields("user_name", "user_num") { (name, num) =>
                fileUpload("user_file") {
                  case (fileInfo, fileStream) =>
                    println(s"上传文件：${fileInfo.fileName}")
                    val parentPath = Paths.get("G:/新建文件夹") resolve new SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS").format(new Date())
                    Files.createDirectories(parentPath)
                    val sink = FileIO.toPath(parentPath resolve fileInfo.fileName)
                    val writeResult = fileStream.runWith(sink)
                    onSuccess(writeResult) { result =>
                      result.status match {
                        case Success(_) => complete(HttpEntity(
                          ContentTypes.`text/html(UTF-8)`,
                          s"""
                            <div>姓名</div>
                            <div>$name</div>
                            <div>学号</div>
                            <div>$num</div>
                            <div>文件名称</div>
                            <div>${fileInfo.fileName}</div>
                            """.stripMargin))
                        case Failure(e) => throw e
                      }
                    }
                }
              }
            }
          }
        }
      } ~
      path("systemInfo") {
        get {
          def run(actor: ActorRef): Future[Done] = {
            /*val promise = Promise[Long]
            val timer = new Timer
            val timeTask = new TimerTask {
              override def run: Unit = {
                promise.trySuccess(System.currentTimeMillis)
                timer.cancel
              }
            }
            timer.schedule(timeTask, 600)
            promise.future.flatMap { num =>
              actor ! num
              run(actor)
            }*/
            DiskInfoReader.getMessage.flatMap { per =>
              actor ! per
              run(actor)
            }.andThen {
              case Failure(e) =>
                e.printStackTrace
              case _ =>
            }
          }

          /*val source = Source
            .actorRef[Double](0, OverflowStrategy.fail)
            .mapMaterializedValue(ref => run(ref))*/

          val source = Source.fromIterator(() => new Iterator[Future[Option[Double]]] {
            override def hasNext: Boolean = true
            override def next(): Future[Option[Double]] = DiskInfoReader.getMessage
          }).mapAsync(1)(identity)

          val source1 = source.collect { case Some(longValue) => longValue }.map(i => ByteString(i.toString) ++ ByteString("<br />"))

          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, source1))
        }
      }

    val port = 8080
    val interface = "127.0.0.1"
    val bindingFuture = Http().bindAndHandle(route, interface = interface, port = port)

    println(s"Server online at http://$interface:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}