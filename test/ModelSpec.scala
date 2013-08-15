import battle.CreateBoard

import com.rethinkscala.ast.Literal
import com.rethinkscala.CurrentSchema
import models._
import models.Board
import models.Point
import org.specs2.mutable.Specification
import play.api.test.WithServer

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/9/13
 * Time: 10:40 AM 
 */
class ModelSpec extends Specification {

  import TestSchema._

  sequential

  import com.rethinkscala.r
  import com.rethinkscala.Implicits._

  "profile" should {

    "create stats record" in new WithServer {

      TestSchema.setup
      CurrentSchema(Some(TestSchema))

      stats.delete.run
      val p = profiles.insert(Seq(Profile("a", "b", "c", "a", "b"), Profile("b", "c", "d", "e", "f"))).run


      (stats get "a" toOpt) must beSome
      (stats get "b" toOpt) must beSome

    }
  }


  "war" should {

    "update total battle count stats" in new WithServer {
      CurrentSchema(Some(TestSchema))

      var war = War.create("a", "b")
      war must beSome

      val ss = stats.filter(s => (s \ "id").eq("b": Literal).or((s \ "id").===("b"))).as[Stats].right.get
      ss.size mustEqual 5


      ss(0).battles mustEqual 1
      ss(1).battles mustEqual 1


    }

  }

  "points" should {

    "update states" in new WithServer {

      CurrentSchema(Some(TestSchema))
      Point(profileId = "a", warId = "1", context = Board("board1", "1", "a", "board", Technology, "http://google.com", 1000)).save

      var stat = (stats get "a" toOpt)
      stat map (_.points) getOrElse (0) === 1000
    }
  }


}
