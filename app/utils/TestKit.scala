package utils

import scala.collection.mutable.ArrayBuffer
import akka.actor.Actor

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/22/13
 * Time: 2:34 PM 
 */
class TestKit {

}


trait TestAble {
  this: Actor =>

  override def preStart() {

    TestKit.addToStack(this)
  }


  override def postStop() {


    TestKit.removeToStack(this)
  }
}

object TestKit {


  private val stack = new scala.collection.mutable.HashMap[Class[_], ArrayBuffer[AnyRef]]

  def last[T](implicit mf: Manifest[T]): T = stack.get(mf.runtimeClass).map(_.lastOption.asInstanceOf[Option[T]]).flatten.get


  def addToStack(obj: AnyRef) = {
    val runtimeClass = obj.getClass
    val buffer: ArrayBuffer[AnyRef] = stack.get(runtimeClass).getOrElse {
      val buffer = ArrayBuffer.empty[AnyRef]
      stack.put(runtimeClass, buffer)
      buffer
    }
    buffer.append(obj)
  }

  def removeToStack(obj: AnyRef) = {
    val runtimeClass = obj.getClass
    stack.get(runtimeClass).map {
      buffer: ArrayBuffer[_] => for {
        index <- Option(buffer.indexOf(obj))

      } yield if (index > -1) buffer.remove(index)
    }
  }


}
