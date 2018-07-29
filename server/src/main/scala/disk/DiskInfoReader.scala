package net.scalax.test01

import java.io.{ BufferedReader, InputStreamReader }
import java.nio.file.Paths
import java.util.{ Timer, TimerTask }
import java.util.concurrent.ForkJoinPool

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Success, Try }

object DiskInfoReader {

  implicit val ec = ExecutionContext.fromExecutor(new ForkJoinPool(6))

  def getMessage(implicit materializer: Materializer): Future[Option[Double]] = {
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
    timer.schedule(timeTask, 3000)

    def getInfo: Future[Option[(Long, Long, Long)]] = {
      def extractString(str: String) = Source.single(ByteString(str))
        .via(Framing.delimiter(ByteString(','), 256, true).map(_.utf8String.trim))
        .drop(1).take(4).toMat(Sink.collection[String, List[String]])(Keep.right).run

      val sink1: Sink[Option[(Long, Long, Long)], Future[Option[(Long, Long, Long)]]] = Sink.fold[Option[(Long, Long, Long)], Option[(Long, Long, Long)]](Option.empty) {
        case (Some((start1, end1, use1)), Some((start2, end2, use2))) => Option((Math.min(start1, start2), Math.max(end1, end2), use1 + use2))
        case (Some((start1, end1, use1)), None) => Option((start1, end1, use1))
        case (None, Some((start2, end2, use2))) => Option((start2, end2, use2))
        case _ => Option.empty
      }

      FileIO.fromPath(Paths.get("g:", "磁盘监控", "output.txt"))
        .via(Framing.delimiter(ByteString('\n'), 9999, true).map(_.utf8String))
        .dropWhile(s => !((s.indexOf("IO Type") >= 0) && (s.indexOf("Start Time") >= 0))).drop(1)
        .filter(s => !s.trim.isEmpty)
        .mapAsync(1) { s =>
          extractString(s).map {
            case List(start, end, notUse, use) =>
              Try {
                //println(start.toLong, end.toLong, use.toLong)
                (start.toLong, end.toLong, use.toLong)
              }.toOption
            case _ =>
              Option.empty
          }
        }
        .toMat(sink1)(Keep.right).run
    }

    def delayFuture = {
      val promise = Promise[Done]
      val timer = new Timer
      val timeTask = new TimerTask {
        override def run: Unit = {
          promise.trySuccess(Done)
          timer.cancel
        }
      }
      timer.schedule(timeTask, 2000)
      promise.future
    }

    for {
      _ <- execCommand("cmd", "/c", "wpr", "-start", "diskio")
      _ <- timeF
      _ <- execCommand("cmd", "/c", "wpr", "-stop", "g:/磁盘监控/aa.etl")
      _ <- execCommand("cmd", "/c", "xperf", "-i", "g:/磁盘监控/aa.etl", "-o", "g:/磁盘监控/output.txt", "-a", "diskio", "-detail")
      data <- getInfo
      (_: Done) <- delayFuture
    } yield {
      data match {
        case Some((start, end, use)) =>
          println(s"生成参数成功:$start/$end/$use")
          Option(use.toDouble / (end - start).toDouble)
        case _ =>
          println(s"生成参数失败")
          Option.empty
      }
    }

  }

}