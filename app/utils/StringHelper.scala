package utils

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/12/13
 * Time: 12:22 AM 
 */
object StringHelper {

  def lowerCaseWithUnderscore(value: String) = value.replace("$", "").zipWithIndex.map {
    case (c, i) => (if (Character.isUpperCase(c) && i > 0) "_" else "") + c
  }.mkString.toLowerCase

}
