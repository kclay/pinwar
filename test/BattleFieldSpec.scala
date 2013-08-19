import akka.actor.{Actor, Props}
import akka.testkit.{TestKitBase, TestActorRef, TestKit}
import akka.util.Timeout
import battle._
import battle.ChannelContext
import battle.Connection
import battle.WarAction
import java.util.UUID
import models.War
import org.specs2.mutable.{After, Specification}
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

  class TestChannelContext(val ref: TestActorRef[Connection]) extends ChannelContext(ref.path)

  sequential

  abstract class Battle extends WithApplication  {
    private lazy val _bf = new BattleField

    implicit def bf = _bf

    implicit def system = bf.system

    override def after = {
      system.shutdown()
      system.awaitTermination()
    }
  }

  def withWar(implicit bf: BattleField) = {


    class DummyActor extends Actor {
      def receive = {
        case _ =>
      }
    }

    val creator = profile("foo")
    val opponent = profile("bar")

    val dummy = bf.system.actorOf(Props(new DummyActor))

    val finder = bf.find(creator, dummy, Timeout(50, SECONDS))

    finder.resolve(opponent.id, true)
    (finder, creator, opponent)

  }

  class BattleField extends battle.BattleField {
    override val blacklistTimeout: FiniteDuration = FiniteDuration(5, SECONDS)
  }

  def profile(id: String)(implicit bf: BattleField) = {
    import TestSchema._
    implicit val system = bf.system

    val (_, channel) = Concurrent.broadcast[JsValue]
    var trench = bf trench

    val c = TestActorRef(Connection(channel), name = s"profile_${id}")
    trench += (id -> new TestChannelContext(c))
    val p = models.Profile(id, "a", "b", "b", "a")
    p.save
    bf.caches.profiles.set(id, p)
    p
    // p
  }

  def battleField = new BattleField()

  "battle" should {


    "release blacklist after 5 seconds" in new Battle {


      val (_, channel) = Concurrent.broadcast[JsValue]
      val profileId = profile("foo").id


      var trench = bf trench

      def ctx = (trench get profileId get)


      trench block (profileId)

      ctx.blacklisted must beSome

      block(6)

      ctx.blacklisted must beNone


    }

    "mark opponent as already seen" in new Battle {


      class DummyActor extends Actor {
        def receive = {
          case _ =>
        }
      }

      val creator = profile("foo")
      val opponent = profile("bar")

      val dummy = bf.system.actorOf(Props(new DummyActor))

      val finder = bf.find(creator, dummy, Timeout(30, SECONDS))

      finder.resolve(opponent.id, false)

      finder.seen(opponent.id) mustEqual true


    }


    "create new war" in new Battle {
      val (finder, _, _) = withWar
      finder.future must beAnInstanceOf[models.War].await


    }


    "have creator as winner" in new Battle {


      import models.Schema
      import models.Stats
      import TestSchema.connection


      val creator = profile("foo")
      val opponent = profile("bar")

      val cCtx = (bf.trench get "foo" get).asInstanceOf[TestChannelContext]
      val oCtx = (bf.trench get "bar" get).asInstanceOf[TestChannelContext]

      val creatorStats = creator.stats

      val opponentStats = opponent.stats

      var war = War.create(creator.id, opponent.id).get

      val dur = Duration(5, SECONDS)

      val creatorRef = oCtx.ref
      val ref = TestActorRef(new WarBattle(war, creator.id, opponent.id, cCtx.actorPath, oCtx.actorPath))


      block(2)
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
