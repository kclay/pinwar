package actions

import com.rethinkscala.net.Connection
import play.api.mvc.{Action, Result, AnyContent, Request}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/17/13
 * Time: 1:46 PM 
 */
trait WithCors {
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
