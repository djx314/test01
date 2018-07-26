package net.scalax.test02

import akka.util.Timeout

trait TimeAction {

  def exec(timeOut: Timeout): String

}

class CreateAction(name: String) extends TimeAction {
  override def exec(timeOut: Timeout): String = {
    "create" + name
  }
}

class UpdateAction(index: Int) extends TimeAction {
  override def exec(timeOut: Timeout): String = {
    "update" + index.toString
  }
}

object MainObject {

  def execTimeOut(action: TimeAction): String = {
    action.exec(???)
  }

  val action1 = new CreateAction("name1")
  val action2 = new UpdateAction(666)
  val action3 = new UpdateAction(777)
  val action4: TimeAction = new TimeAction {
    override def exec(timeOut: Timeout): String = {
      "name4"
    }
  }

  execTimeOut(action1)
  execTimeOut(action2)
  execTimeOut(action3)
  execTimeOut(action4)

}