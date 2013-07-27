import battle.ChallengeResponse
import battle.ChallengeResponse
import battle.Find
import battle.Find
import java.net.URI
import java.util.concurrent.TimeUnit._
import models.Profile
import models.Profile
import models.{War, Profile}
import org.specs2.execute.AsResult
import org.specs2.mutable._


import play.api.Logger
import utils.WebSocketClient
import utils.WebSocketClient.Messages._
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent._
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

  import ExecutionContext.Implicits.global


  // sequential
  // forces all tests to be run sequentially

  def block(timeout: Long) = {
    val p = promise[Boolean]
    try {
      await(p.future, timeout, SECONDS)
    } catch {
      case e: TimeoutException =>
    }
  }

  abstract class WithWs extends WithServer {
    override def around[T](t: => T)(implicit evidence$2: AsResult[T]) = {
      Logger.info("lalalala")
      super.around(t)(evidence$2)
    }
  }


  val port = testServerPort

  def ws(pf: PartialFunction[WebSocketClient.Messages.WebSocketClientMessage, Unit]) = WebSocketClient(new URI(s"ws://localhost:${port}/war/123456"))(pf)

  def ws(id: String)(pf: PartialFunction[WebSocketClient.Messages.WebSocketClientMessage, Unit]) = WebSocketClient(new URI(s"ws://localhost:${port}/war/$id"))(pf)

  import java.util.concurrent.TimeUnit._

  "war controller" should {

    /* "fail on find request" in new WithWs {


       val profileId = "foo"


       val result = controllers.Application.index(FakeRequest())

       val p = promise[Boolean]
       ws {
         case Connected(client) => {
           client.send(Find(profileId))
         }
         case ConnectionFailed(client, reason) => p.success(false)
         case TextMessage(client: WebSocketClient, text: String) => {


         }
         case _ =>
       } connect

       await(p.future, 90, SECONDS) must beTrue
     }   */

    "find opponent with accept" in new WithWs {

      val me = Profile("111111", "foo", "foo", "foo@test.com", "")
      val opponent = Profile("222222", "bar", "bar", "bar@test.com", "")
      profiles.insert(Seq(me, opponent)).run match {
        case Left(e) => println(e)
        case Right(r) => println(r)
      }
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

      await(helper.future, 10, SECONDS)

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



              client.send(ChallengeResponse(opponent.id, cr.token, false))
             future{
               client.send(ChallengeResponse(opponent.id, cr.token, accept))
             }



            }
            case _ => p.success(false)
          }
        }
      }
      oWs connect

      await(p.future, 90, SECONDS) must beTrue

    }


  }
}
