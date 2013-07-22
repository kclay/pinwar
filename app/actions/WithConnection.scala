package actions

import com.rethinkscala.net.Connection
import play.api.mvc._

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/15/13
 * Time: 10:59 AM 
 */

trait WithDatabase {

  def Session(f: Request[AnyContent] => Result) = Action {
    implicit request =>
      f(request)
  }

}

object WithConnection {

}
