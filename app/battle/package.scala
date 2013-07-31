/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/29/13
 * Time: 11:45 PM 
 */
package object battle {
  implicit def option2String(o: Option[String]) = o.get
}
