package utils

import com.rethinkscala.reflect.Memo
import java.lang.reflect.Field

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/12/13
 * Time: 12:22 AM 
 */
object StringHelper {

  private[this] val classNames = new Memo[Class[_], String]

  def lowerCaseWithUnderscore(c: Class[_]): String = classNames(c, {
    case _ => lowerCaseWithUnderscore(c.getSimpleName.replace("$", ""))
  })


  def lowerCaseWithUnderscore(value: String): String = value.replace("$", "").zipWithIndex.map {
    case (c, i) => (if (Character.isUpperCase(c) && i > 0) "_" else "") + c
  }.mkString.toLowerCase

}
