package net.scalax.test01

import java.nio.file.{ Files, Paths }
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ForkJoinPool

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.HttpCookiePair
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import scala.concurrent.ExecutionContext
import scala.io.StdIn
import scala.util.{ Failure, Success }

object Runner {
  //implicit val ec = ExecutionContext.fromExecutor(new ForkJoinPool(6))

  lazy val test03 = new Test03

  def main11(args: Array[String]): Unit = {
    implicit val system = ActorSystem("mp4-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route =
      path("hello") {
        get {
          complete(HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              <html>
                <head>
                 <script type="text/javascript">
                 document.cookie = "cookiea=hfioernrhuehrtuierhwer";
                 document.cookie = "cookieb=sdhfshjioehweiojr";
                 document.cookie = "cookiec=sdhiosehfhesrhse";
                 </script>
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

              cookie("cookiea") { cookiea => //没有 cookiea 会返回错误页面
                optionalCookie("cookief") { cookief => //没有 cookief 会变成 None

                  val aa: HttpCookiePair = cookiea //cookiea 没有 option 的类型展示
                  val bb: Option[HttpCookiePair] = cookief //cookief option 的类型展示

                  val aaValue: String = aa.value
                  val bbValue: Option[String] = bb.map(_.value)

                  println(Map("cookiea" -> aaValue, "cookief" -> bbValue))

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
            }
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