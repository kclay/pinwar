package battle

import akka.actor._
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.json.JsValue
import akka.actor.Terminated
import utils.ActorCreator
import scala.collection.immutable

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/7/13
 * Time: 7:10 PM 
 */


object Connection extends ActorCreator {
  def actorFor(profileId: String)(implicit system: ActorSystem) = system.actorSelection(s"/user/profiles/$profileId")


  def apply(bf: BattleField, channel: Channel[JsValue])(implicit system: ActorSystem): ActorRef = apply(props(bf).copy(args = immutable.Seq(channel)), bf.currentMode)

  def props(bf: BattleField) = Props[Connection]
}

case class Connection(channel: Channel[JsValue]) extends Actor with ActorLogging {

  override def unhandled(message: Any) {
    super.unhandled(message)
  }

  var lastMessage: Option[JsValue] = None

  def receive = {
    case Terminated(actor) => log error "Terminated"
    case item: JsValue => {

      channel.push(item)
      lastMessage = Some(item)
      log info s"JsValue = ${item.toString}"
    }


    case x: Any => log error (s"Unhandled = $x")

  }
}

object Connections extends ActorCreator {
  def props(bf: BattleField) = Props(classOf[Connections], bf)
}

class Connections(ctx: BattleField) extends Actor {
  def receive = {
    case Connect(profileId, channel, fromInvite) => {

      val connection = Connection(ctx, channel)(context.system)
      context.watch(connection)
      ctx.trench ! AddUser(profileId, ChannelContext(connection.path, pending = fromInvite))
    }
    case Terminated(actor) => {
      context.unwatch(actor)
      ctx.master ! Disconnect(actor.path.name)
    }


  }

}
