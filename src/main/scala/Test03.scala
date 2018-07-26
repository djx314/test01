package net.scalax.test01

import java.io.{ BufferedReader, File, InputStreamReader }
import java.nio.file.{ Files, Paths }
import java.util.{ Timer, TimerTask }
import java.util.concurrent.{ ForkJoinPool, ThreadPoolExecutor }

import akka.Done

import scala.collection.JavaConverters._
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }

object Test03 {
  implicit val ec = ExecutionContext.fromExecutor(new ForkJoinPool(6))

  def main(args: Array[String]): Unit = {
    val softDir = Paths.get("c:", "Program Files (x86)", "Windows Kits", "10", "Windows Performance Toolkit")

    def execCommand(command: String*): Future[Done] = {
      val pb1 = new ProcessBuilder
      pb1.directory(softDir.toFile)
      pb1.command(command: _*)
      val future: Future[Done] = Future {
        val pro1 = pb1.start
        val proStream1 = pro1.getInputStream
        val proErrStream1 = pro1.getErrorStream

        val fa = Future {
          val inputStreamReader = new InputStreamReader(proStream1)
          val bufferedReader = new BufferedReader(inputStreamReader)
          val it = Iterator.continually(bufferedReader.readLine) takeWhile (_ != null)
          it.map(s => s"正常输出:$s")
          Done
        }

        val fb = Future {
          val inputStreamReader = new InputStreamReader(proErrStream1)
          val bufferedReader = new BufferedReader(inputStreamReader)
          val it = Iterator.continually(bufferedReader.readLine) takeWhile (_ != null)
          it.map(s => s"错误输出:$s")
          Done
        }

        fa.flatMap((_: Done) => fb.map((_: Done) => Done)).map { (_: Done) =>
          println(s"执行成功：${pb1.command.asScala.mkString(" ")}")
          Done
        }
      }.flatten

      future
    }

    val timeP = Promise[Done]
    val timeF = timeP.future
    val timer = new Timer
    val timeTask = new TimerTask {
      override def run(): Unit = {
        timeP.trySuccess(Done)
        timer.cancel
      }
    }
    timer.schedule(timeTask, 5000)

    val a = for {
      _ <- execCommand("cmd", "/c", "wpr", "-start", "diskio")
      _ <- timeF
      _ <- execCommand("cmd", "/c", "wpr", "-stop", "g:/磁盘监控/aa.etl")
      _ <- execCommand("cmd", "/c", "xperf", "-i", "g:/磁盘监控/aa.etl", "-o", "g:/磁盘监控/output.txt", "-a", "diskio", "-detail")
    } yield {
      println("运行完毕")
    }

    Await.result(a, scala.concurrent.duration.Duration.Inf)

  }
}