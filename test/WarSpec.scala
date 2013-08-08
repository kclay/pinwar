import akka.util.Timeout
import java.net.URI
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import models.Profile
import org.specs2.execute.AsResult
import org.specs2.mutable._


import utils.WebSocketClient
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent._
import scala.concurrent.duration._
import battle._
import utils.WebSocketClient.Messages.Connected
import utils.WebSocketClient.Messages.TextMessage

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/12/13
 * Time: 8:16 PM 
 */
class WarSpec extends Specification {


  import models.Schema._
  import play.api.libs.json.Json
  import utils.Serialization.Writes._


  // sequential
  // forces all tests to be run sequentially

  def block(timeout: Long) = {
    val p = promise[Boolean]
    try {
      Helpers.await(p.future)(Timeout(timeout, SECONDS))
    } catch {
      case e: TimeoutException =>
    }
  }

  abstract class WithWs extends WithServer {
    override def around[T](t: => T)(implicit evidence$2: AsResult[T]) = {

      super.around(t)(evidence$2)
    }
  }


  val port = testServerPort

  def setupProfiles = {
    val me = Profile("me", "foo", "foo", "foo@test.com", "")
    val opponent = Profile("opponent", "bar", "bar", "bar@test.com", "")
    val opponent2 = Profile("opponent2", "bar", "bar", "bar@test.com", "")
    profiles.insert(Seq(me, opponent, opponent2)).run
    (me, opponent, opponent2)
  }

  def ws(pf: PartialFunction[WebSocketClient.Messages.WebSocketClientMessage, Unit]) = WebSocketClient(new URI(s"ws://localhost:${port}/war/123456/false"))(pf)

  def ws(id: String)(pf: PartialFunction[WebSocketClient.Messages.WebSocketClientMessage, Unit]) = WebSocketClient(new URI(s"ws://localhost:${port}/war/$id/false"))(pf)

  import java.util.concurrent.TimeUnit._

  "war controller" should {


    "find second opponent with accept" in new WithWs {

      val count = new AtomicInteger()

      val accept = new AtomicBoolean()

      var allConnected = promise[Boolean]

      var warAccepted = promise[Boolean]
      val (me, op, op2) = setupProfiles

      def connected = {
        println("Connected")
        if (count.incrementAndGet() == 3) allConnected.success(true)
      }

      def onMessage(profile: Profile, client: WebSocketClient, text: String) = Json.parse(text) match {
        case Extractor.WarAccepted(wa) => {
          if (profile eq me)
            warAccepted.success(true)
        }

        case Extractor.Countdown(c) => println(c)
        case Extractor.ChallengeRequest(cr) => {


          val response = ChallengeResponse(profile.id, cr.token, accept.get(), me.id)

          client.send(response)

          accept.set(true)


        }
        case x: Any => println(s"Unhandled : $x")
      }


      val meWs = ws(me.id) {
        case Connected(client) => connected
        case TextMessage(client: WebSocketClient, text: String) => onMessage(me, client, text)
        case x: Any => println(x)
      }

      val opWs = ws(op.id) {
        case Connected(client) => connected
        case TextMessage(client: WebSocketClient, text: String) => onMessage(op, client, text)
        case x: Any => println(x)

      }
      val op2Ws = ws(op2.id) {
        case Connected(client) => connected
        case TextMessage(client: WebSocketClient, text: String) => onMessage(op2, client, text)
        case x: Any => println(x)
      }
      Seq(meWs, opWs, op2Ws) foreach (_.connect)



      Helpers.await(allConnected.future, 10, SECONDS)

      meWs.send(Find(me.id))

      Helpers.await(warAccepted.future, 20, SECONDS) mustEqual true


    }

    /*

    "find opponent with accept" in new WithWs {

      val (me, op, op2) = setupProfiles
      val p = promise[Boolean]

      val helper = promise[Boolean]

      val meWs = ws(me.id) {
        case Connected(client) => {

          client.send(Find(me.id))
          helper.success(true)
        }
        case TextMessage(client: WebSocketClient, text: String) => {
          println(s"Creator -> $text")
          Json.parse(text) match {
            case Extractor.WarAccepted(wa) => p.success(true)

            case Extractor.Countdown(c) => println(c)
            case a: Any => {
              println(a)
              p.success(false)
            }

          }
        }
      } connect


      block(10)

      var accept = false
      val oWs = ws(opponent.id) {
        case Connected(client) => {
          println("Opponented connect")
        }
        case Disconnected(client, reason) => if (accept) {

          println("Reconnecting")

        }
        case TextMessage(client: WebSocketClient, text: String) => {
          println(s"Opponent -> $text")
          Json.parse(text) match {
            case Extractor.ChallengeRequest(cr) => {


              client.send(ChallengeResponse(opponent.id, cr.token, false, me.id))
              future {
                client.send(ChallengeResponse(opponent.id, cr.token, accept, me.id))
              }


            }
            case _ => p.success(false)
          }
        }
      }
      oWs connect

      Helpers.await(p.future)(Timeout(90, SECONDS)) must beTrue

    }   */


  }
}
