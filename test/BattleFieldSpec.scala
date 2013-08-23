import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import akka.util.Timeout
import battle._
import battle.Connection
import battle.WarAction
import java.util.UUID
import models.War
import org.specs2.mutable.Specification
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsValue
import utils.TestKit


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/12/13
 * Time: 8:12 PM 
 */
class BattleFieldSpec extends Specification with Helpers {

  import scala.concurrent.duration._

  implicit val timeout = Timeout(30, SECONDS)

  import akka.pattern.ask

  abstract class Battle extends WithApplication {
    private lazy val _bf = {
      val b = new BattleField
      b.master
      b
    }

    implicit def bf = _bf

    implicit def system = bf.system

    override def after = {
      system.shutdown()
      system.awaitTermination()
    }
  }

  type ConnectionRef = ChannelContext
  sequential

  class BattleField extends battle.BattleField {
    override val blacklistTimeout: FiniteDuration = FiniteDuration(5, SECONDS)
  }

  def profile(id: String)(implicit bf: BattleField) = {
    import TestSchema._
    implicit val system = bf.system

    val (_, channel) = Concurrent.broadcast[JsValue]

    val p = models.Profile(id, "a", "b", "b", "a")
    p.save
    bf.caches.profiles.set(id, p)

    bf.connections ! Connect(id, channel, false)


    p
    // p
  }

  def battleField = new BattleField()

  "battle" should {

    "release blacklist after 5 seconds" in new Battle {


      val profileId = profile("foo").id


      var state = bf state

      val trench = bf.trench

      def ctx = (state get profileId get)


      trench ! Block(profileId)

      ctx.blacklisted must beSome

      block(6)

      ctx.blacklisted must beNone


    }

    "mark opponent as already seen" in new Battle {


      val creator = profile("foo")
      val opponent = profile("bar")


      bf.finders ! Find("foo")

      val ref = withValue(Finders.identify("foo"), 5)
      ref ! ResolveChallenge(opponent.id, false)

      val finder = TestKit.last[Finder]

      finder seen (opponent.id) must beTrue


    }

    def withWar(implicit bf: BattleField, system: ActorSystem) = {


      val creator = profile("foo")
      val opponent = profile("bar")


      bf.finders ! Find("foo")


      val ref = withValue(Finders.identify("foo"), 5)
      ref ! ResolveChallenge(opponent.id, true)
      val finder = TestKit.last[Finder]
      (finder, creator, opponent)

    }

    "create new war" in new Battle {


      val (finder, _, _) = withWar

      finder.future must beAnInstanceOf[models.War].await(2)

    }

    "have creator as winner" in new Battle {


      import models.Schema
      import models.Stats
      import TestSchema.connection


      val creator = profile("foo")
      val creatorRef = TestKit.last[Connection]
      val opponent = profile("bar")
      val opponentRef = TestKit.last[Connection]


      //val creatorRef = (bf.state get "foo" get).asInstanceOf[ConnectionRef]
      // val opponentRef = (bf.state get "bar" get).asInstanceOf[ConnectionRef]

      val creatorStats = creator.stats

      val opponentStats = opponent.stats

      var war = War.create(creator.id, opponent.id).get

      val dur = Duration(5, SECONDS)


      val ref = TestActorRef(new WarBattle(war, creator.id, opponent.id, Connection("foo"), Connection("bar")))

      block(2)
      ref ! WarAction("foo", war.id.get, CreateBoard(UUID.randomUUID().toString, "foo", "#pinterestwars", war.category, "http://google.com", false))




      (creatorRef.lastMessage match {
        case Some(j) => (j \ "data" \ "profileId").as[String]
        case _ => ""
      }) mustEqual ("foo")

      war = Schema[War].get(war.id.get).toOpt get

      war.winnerId must beSome.which(_ === "foo")


      val afterCreatorStats = Schema[Stats].get(creator.id).toOpt.get
      afterCreatorStats.wins must be_>(creatorStats.wins)

      val afterOpponentStats = Schema[Stats].get(opponent.id).toOpt.get

      afterOpponentStats.loses must be_>(opponentStats.loses)


    }

    "create pending rematch request" in new Battle {


      val creator = profile("foo")
      val opponent = profile("bar")

      val f = bf.master.ask(Rematch(creator.id, opponent.id)).mapTo[RematchContext]

      val rematch = withValue(f, 10)
      rematch.profile.id mustEqual (opponent.id)

      bf.invites must haveKey(rematch.token)


    }
    "create pending invite request" in new Battle {


      val creator = profile("foo")
      val email = "keyston@likeus.co"
      val f = bf.master.ask(Invite(creator.id, email)).mapTo[String]
      val token = withValue(f, 10)

      val key = s"invite_${token}"
      // Cache.set(key, email, 30 * 60)

      // Cache.getAs[String](key) must beSome
      bf.caches.invites(token, email)
      // bf.caches.invites(token, email)


      bf.invites must haveKey(token)

      bf.caches.invites.as[String](token) must beSome
    }

  }

}
