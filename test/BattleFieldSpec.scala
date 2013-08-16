import akka.actor._
import akka.testkit.{TestKitBase, TestActorRef, TestKit}
import akka.util.Timeout
import battle._
import battle.ChannelContext
import battle.Connection
import battle.WarAction
import java.util.UUID
import models.War
import org.specs2.mutable.Specification
import play.api.cache.Cache
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsValue


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


  type ConnectionRef = TestActorRef[Connection]
  sequential

  class BattleField extends battle.BattleField {
    override val blacklistTimeout: FiniteDuration = FiniteDuration(5, SECONDS)
  }

  def profile(id: String)(implicit bf: BattleField) = {
    import TestSchema._
    implicit val system = bf.system

    val (_, channel) = Concurrent.broadcast[JsValue]
    val trench = bf trench
    val p = models.Profile(id, "a", "b", "b", "a")
    p.save
    bf.caches.profiles.set(id, p)

    trench ! Connect(id, channel, false)


    p
    // p
  }

  def battleField = new BattleField()

  "battle" should {

    "release blacklist after 5 seconds" in new WithApplication {

      implicit val bf = battleField

      val profileId = profile("foo").id


      var state = bf state

      val trench = bf.trench

      def ctx = (state get profileId get)


      trench ! Block(profileId)

      ctx.blacklisted must beSome

      block(6)

      ctx.blacklisted must beNone


    }

    "mark opponent as already seen" in new WithApplication {
      implicit val bf = battleField
      implicit val system = bf.system

      class DummyActor extends Actor {
        def receive = {
          case _ =>
        }
      }

      val creator = profile("foo")
      val opponent = profile("bar")


      bf.finders ! Find("foo")

      val ref = withValue(Finders.identify("foo"), 5).asInstanceOf[TestActorRef[Finder]]
      ref ! ResolveChallenge(opponent.id, false)




      ref.underlyingActor.seen(opponent.id) mustEqual true


    }

    def withWar = {
      implicit val bf = battleField
      implicit val system = bf.system

      class DummyActor extends Actor {
        def receive = {
          case _ =>
        }
      }

      val creator = profile("foo")
      val opponent = profile("bar")


      bf.finders ! Find("foo")

      val ref = withValue(Finders.identify("foo"), 5).asInstanceOf[TestActorRef[Finder]]

      ref.underlyingActor.resolve(opponent.id, true)
      (bf, ref.underlyingActor, creator, opponent)

    }

    "create new war" in new WithApplication {
      val (bf, finder, _, _) = withWar
      finder.future must beAnInstanceOf[models.War].await
    }

    "have creator as winner" in new WithApplication {
      implicit val bf = battleField

      implicit val system = bf.system


      import models.Schema
      import models.Stats
      import TestSchema.connection


      val creator = profile("foo")
      val opponent = profile("bar")


      val creatorRef = (bf.state get "foo" get).asInstanceOf[ConnectionRef]
      val opponentRef = (bf.state get "bar" get).asInstanceOf[ConnectionRef]

      val creatorStats = creator.stats

      val opponentStats = opponent.stats

      var war = War.create(creator.id, opponent.id).get

      val dur = Duration(5, SECONDS)


      val ref = TestActorRef(WarBattle(war, creator.id, opponent.id, creatorRef.path, opponentRef.path))


      ref ! WarAction("foo", war.id.get, CreateBoard(UUID.randomUUID().toString, "foo", "#pinterestwars", war.category, "http://google.com"))



      (creatorRef.underlyingActor.lastMessage match {
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

    "create pending rematch request" in new WithApplication {
      implicit val bf = battleField


      val creator = profile("foo")
      val opponent = profile("bar")

      val f = bf.master.ask(Rematch(creator.id, opponent.id)).mapTo[RematchContext]

      val rematch = withValue(f, 10)
      rematch.profile.id mustEqual (opponent.id)

      bf.invites must haveKey(rematch.token)


    }
    "create pending invite request" in new WithApplication {
      implicit val bf = battleField


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
