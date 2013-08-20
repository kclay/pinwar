package utils

import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.json.{Json, JsValue}
import play.api.libs.iteratee.{Input, Concurrent}
import akka.actor.ActorSystem
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/20/13
 * Time: 11:58 AM 
 */
class WatchedChannel(underlying: Channel[JsValue], system: ActorSystem) extends Channel[JsValue] {


  import scala.concurrent.duration._

  import scala.concurrent.ExecutionContext.Implicits.global


  val sent = new AtomicBoolean(false)
  /*
  val handler = system.scheduler.schedule(40 seconds, 40 seconds) {
    if (!sent.get()) {
      underlying.push(ping)
    }
    sent.set(false)
  } */

  val ping = Json.obj("event" -> "ping",
    "data" -> Json.obj())

  def push(chunk: Input[JsValue]) {

    underlying.push(chunk)
    sent.set(true)
  }

  def end(e: Throwable) {
    underlying.end(e)
  }

  def end() {
    underlying.end()
  }

  def done {
    handler.cancel()
  }


}

