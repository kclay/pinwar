import java.net.URI
import org.specs2.execute.AsResult
import org.specs2.mutable._
import org.specs2.time.NoTimeConversions


import play.api.libs.ws.WS
import play.api.Logger
import play.api.test.{WithServer}
import utils.WebSocketClient
import utils.WebSocketClient.Messages.{TextMessage, ConnectionFailed, Connected}
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent._

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/12/13
 * Time: 8:16 PM 
 */
class WarSpec extends Specification {

  import battle._
  import Writes._
  import play.api.libs.json.Json

  // sequential
  // forces all tests to be run sequentially


  abstract class WithWs extends WithServer {
    override def around[T](t: => T)(implicit evidence$2: AsResult[T]) = {
      Logger.info("lalalala")
      super.around(t)(evidence$2)
    }
  }


  implicit def eventToString(e: Event): String = Json.toJson(e).toString()

  val port = testServerPort

  def ws(pf: PartialFunction[WebSocketClient.Messages.WebSocketClientMessage, Unit]) = WebSocketClient(new URI(s"ws://localhost:${port}/war/123456"))(pf)

  import java.util.concurrent.TimeUnit._

  "war controller" should {

    "fail on find request" in new WithWs {


      val profileId = "foo"


      val result = controllers.Application.index(FakeRequest())

      val p = promise[Boolean]
      ws {
        case Connected(client) => {
          client.send(Find(profileId, 10))
        }
        case ConnectionFailed(client, reason) => p.success(false)
        case TextMessage(client: WebSocketClient, text: String) => {


        }
        case _ =>
      } connect

      await(p.future, 90, SECONDS) must beTrue
    }


  }
}
