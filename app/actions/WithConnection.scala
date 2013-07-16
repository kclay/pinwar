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

  def Session(f: Connection => Request[AnyContent] => Result) = {

  }

}

object WithConnection {

}
