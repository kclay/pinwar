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


  "lala " should {

    "work" in new WithServer {
      private val host = configuration.getString("rethink.default.url", None)
      private val version = Version1(host.getOrElse("localhost"))
      implicit val connection = Connection(version)

      val wars = r.table[War]("testing_wars")
      wars.create.run

      val cats = Category.all
      wars.insert(Seq(
        War("1", "a", "b", cats(0)), War("2", "a", "b", cats(1)), War("3", "a", "b", cats(2))
      )) run

      val creatorId = "a"
      val opponentId = "b"
      val names = cats.map {
        c => c.name: Datum
      }
      val q = (wars.filter {
        v => v \ "creatorId" === creatorId or v \ "opponentId" === creatorId or v \ "creatorId" === opponentId or v \ "opponentId" === opponentId
      } filter {
        v => v \ "createdAt" >= 1
      }) \ "category" idiff (names: _*)


      val ast = q ast


      q.as[String] match {
        case Left(e) => println(e)

        case Right(r) => println(r)
      }
    }

  }
}
