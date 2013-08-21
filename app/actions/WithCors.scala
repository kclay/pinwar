package actions

import com.rethinkscala.net.Connection
import play.api.mvc._
import play.api.libs.iteratee.Iteratee

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/17/13
 * Time: 1:46 PM 
 */
trait WithCors {

  import play.api.http.HeaderNames
  /*
  case class AllowCors(origin: String)(action: EssentialAction) extends EssentialAction {

    def apply(request: RequestHeader): Iteratee[Array[Byte], SimpleResult] = {
      val ok = (origin match {
        case "*" => true
        case _ => request.headers.get(HeaderNames.ORIGIN).map(_.equals(origin)).getOrElse(false)
      })
      if(ok)action(request).

    }
  }

  object AllowCors {

    def apply(origin: String)(action: EssentialAction) {

    }
  }

  def AllowCors(origin: String) = {

  }   */

  def AllowCors(f: Request[AnyContent] => Result) = Action {
    implicit request =>
      f(request).withHeaders(
        "Access-Control-Allow-Origin" -> "*",
        "Access-Control-Allow-Methods" -> "GET, POST",
        "Access-Control-Allow-Headers" -> "Content-Type, X-Requested-With, Accept",
        // cache access control response for one day
        "Access-Control-Max-Age" -> (60 * 60 * 24).toString

      )
  }
}
