package battle

import akka.actor._
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.json.JsValue
import akka.actor.Terminated

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/7/13
 * Time: 7:10 PM 
 */

case class Connection(channel: Channel[JsValue]) extends Actor with ActorLogging {

  override def unhandled(message: Any) {
    super.unhandled(message)
  }

  var lastMessage: Option[JsValue] = None

  def receive = {
    case Terminated(actor) =>
    case item: JsValue => {

      channel.push(item)
      lastMessage = Some(item)
      log info s"JsValue = ${item.toString}"
    }


    case x: Any => log info (s"Unhandled = $x")
    case _ =>
  }
}

class ConnectionSupervisor(ctx: BattleField) extends Actor {
  def receive = {
    case Connect(profileId, channel, fromInvite) => {
      val connection = context.system.actorOf(Props(Connection(channel)), name = profileId)
      context.watch(connection)
      ctx.trench += (profileId -> ChannelContext(connection.path, pending = fromInvite))
    }
    case Terminated(actor) => {
      context.unwatch(actor)
      ctx.master ! Disconnect(actor.path.name)
    }


  }

}
