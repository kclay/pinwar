package battle

import scala.collection.mutable
import scala.collection.script.{Remove, Update, Include, Message}
import play.api.libs.json.JsValue
import play.api.Logger
import akka.actor.ActorSystem

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/23/13
 * Time: 7:30 PM 
 */


trait TrenchContext {

  val system: ActorSystem
}

class Trench(ctx: BattleField) extends mutable.HashMap[String, ChannelContext] with mutable.ObservableMap[String, ChannelContext] {
  type Pub = Trench

  private val log = Logger(getClass)


  def :=+(profileId: String) = context(profileId, true)

  def :=-(profileId: String) = context(profileId, false)

  def block(profileId: String) = withBlock(profileId, true)

  def unblock(profileId: String) = withBlock(profileId, false)

  private def withBlock(profileId: String, block: Boolean): Unit = get(profileId) map {
    c => {

      val pending = block
      this += (profileId -> c.copy(blacklisted = block match {
        case true if (c.blacklisted.isDefined) => c.blacklisted
        case true => Some(ctx.system.scheduler.scheduleOnce(ctx.blacklistTimeout) {
          log.debug(s"Unblocking $profileId")
          unblock(profileId)
        }(ctx.system.dispatcher))
        case false if (c.blacklisted.isDefined) => c.blacklisted.get.cancel(); None
        case false => None
      }, pending = pending))

    }


  }


  def context(profileId: String, pending: Boolean) = get(profileId) map {
    c => if (c.pending != pending) this += (profileId -> c.copy(pending = pending))


  }
}


trait FinderStrategy {

  type Apply = PartialFunction[(String, ChannelContext), Unit]

  def apply(trench: Trench): Apply
}

case class DefaultFindStrategy(ctx: BattleField) extends FinderStrategy {
  private val logger = Logger(getClass)

  def apply(trench: Trench): Apply = {
    case (opponentId: String, _: ChannelContext) =>
      ctx.pendingFinders.headOption map {
        finder => {

          logger.info("Found a pending users, removing from queue and resolving Finder")
          ctx.pendingFinders.remove(0)
          ctx.finders += finder
          // update opponent state
          trench :=+ opponentId
          finder request opponentId

        }
      } orElse {
        logger.info("No pending users waiting")
        None
      }

  }
}


class TrenchSub(ctx: BattleField) extends mutable.Subscriber[Message[(String, ChannelContext)] with mutable.Undoable, Trench] {
  private val logger = Logger(getClass)
  type Sub = (String, ChannelContext)

  import scala.concurrent.future


  //Use the system's dispatcher as ExecutionContext

  implicit def toCtx(m: {val elem: Sub}) = m.elem._2

  def inviting(m: {val elem: Sub}) = ctx.invitesIds.contains(m.elem._1)

  def notify(pub: Trench, event: Message[Sub] with mutable.Undoable): Unit = future({
    // TODO : This should take the common boards that each player has and pick the correct users to play against for the first pass
    // the second pass will check if we have a board theme for the common board, if not its a freeplay
    (event match {
      case i: Include[Sub] if (!inviting(i)) => Some(i.elem)
      case u: Update[Sub] if (!u.pending && u.blacklisted.isEmpty && !inviting(u)) => Some(u.elem)
      case r: Remove[Sub] => r.destroy; None
      case _ => None
    }) map ctx.findStrategy(pub)

  })(scala.concurrent.ExecutionContext.Implicits.global)


}
