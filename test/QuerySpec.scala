import com.rethinkscala.ast.{ProduceAnySequence, Expr, Datum}
import com.rethinkscala.net.Version1
import com.rethinkscala.net.{Connection, Version1}

import org.joda.time.{Period, DateTime}
import org.specs2.mutable.Specification
import play.api.Play._
import play.api.test.{WithServer, TestServer}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/29/13
 * Time: 8:00 PM 
 */
class QuerySpec extends Specification {


  import com.rethinkscala.Implicits._
  import models._
  import com.rethinkscala.r

  /*
  "lala " should {

    "work" in new WithServer {

      import models.Schema._

      // private val host = configuration.getString("rethink.default.url", None)
      //private val version = Version1(host.getOrElse("localhost"))
      //implicit val connection = Connection(version)

      profiles.run match {
        case Left(e) => println(e)
        case Right(r) => stats.insert(r.map {
          p => Stats(p.id)
        }).run match {
          case Left(e) => println(e)
          case Right(r) => println(r)
        }

      }


    }

  }   */
}
