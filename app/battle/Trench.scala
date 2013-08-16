package battle

import scala.collection.mutable
import scala.collection.script.Message
import play.api.libs.json.JsValue
import play.api.Logger
import akka.actor._
import models.ChallengeToken

import akka.routing.FromConfig
import scala.collection.script.Remove
import scala.collection.script.Update
import scala.Some
import models.Profile
import scala.collection.script.Include
import utils.ActorCreator

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/23/13
 * Time: 7:30 PM 
 */


case class MarkPending(ids: String*)

case class UnMarkPending(profileId: String)

case class Block(profileId: String)

case class Unblock(profileId: String)

case class AddUser(profileId: String, c: ChannelContext)

case class RemoveUser(profileId: String)

case class FindOpponent(requesterId: String, filter: Seq[String] = Seq.empty, requester: Option[(ActorSelection, Profile)] = None)


case class GetContext(profileId: String)

case class RequestChallenge(finder: ActorSelection, profile: Profile, opponentId: String)

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
      case r: Remove[Sub] => /*r.destroy;*/ None
      case _ => None
    }) map ctx.findStrategy(pub)

  })(scala.concurrent.ExecutionContext.Implicits.global)


}


object Trench extends ActorCreator {

  def props(bf: BattleField) = Props(classOf[Trench], bf).withRouter(FromConfig)

}

case class Trench(scope: BattleField) extends Actor with ActorLogging {

  import scope._
  import utils.Serialization.Writes._

  implicit val ec = context.system.dispatcher
  val scheduler = context.system.scheduler
  implicit val system = context.system

  def receive = {


    case AddUser(profileId, c) => state += (profileId -> c)

    case RemoveUser(profileId) => state.remove(profileId) map {
      cc => {
        log.info(s"Sending PoisonPill to $profileId")
        cc ! PoisonPill
        invitesIds -= profileId

        finders ! DestroyFinder(profileId)


        log error "Need to clean up challenge tokens"
        //challengeTokens retain ((t, f) => f.creatorId != profileId)
        invites.retain {
          case (k, (creatorId, _)) => creatorId != profileId
        }
      }

    }


    case MarkPending(profileId) => withPending(profileId, true)
    case UnMarkPending(profileId) => withPending(profileId, false)
    case Block(profileId) => block(profileId)
    case Unblock(profileId) => unblock(profileId)

    case GetContext(profileId) => sender ! state.get(profileId)

    case RequestChallenge(ref, profile, opponentId) => state.get(opponentId) map {
      c => withPending(opponentId, true)
        // TODO Send email

        ChallengeToken(ref) map {
          t => {
            val id = t.id.get
            scope.challengeTokens += (id -> ref)
            c ! (ChallengeRequest(id, profile): JsValue)
          }

        }


    }

    case FindOpponent(requesterId, filter, requester) => {
      val found = state.collectFirst {
        case (p, c) if (p != requesterId && c.available && !filter.contains(p)) => {
          withPending(p, true)
          p
        }
      }
      sender ! found






      for {

        (finder, profile) <- requester
      } yield found match {
        case Some(opponentId) => {
          log.info(s"Found a opponent for ${profile.name} to battle ${profileFor(opponentId).name}")
          self ! RequestChallenge(finder, profile, found)
        }
        case _ => {
          log.info(s"Couldn't find any available user, putting ${profile.name} into `listen` state")
          log error "TODO : ReQueue Finder"
          //finders ! QueueFinder(finder)
        }
      }
    }


  }


  def block(profileId: String) = withBlock(profileId, true)

  def unblock(profileId: String) = withBlock(profileId, false)

  def withPending(profileId: String, pending: Boolean) {
    state.get(profileId) map {
      c => state += (profileId -> c.copy(pending = pending))


    }
  }

  def withBlock(profileId: String, block: Boolean): Unit = state.get(profileId) map {
    c => {

      val pending = if (block) c.pending else false
      state += (profileId -> c.copy(blacklisted = block match {
        case true if (c.blacklisted.isDefined) => c.blacklisted
        case true => Some(scheduler.scheduleOnce(blacklistTimeout) {
          log.debug(s"Unblocking $profileId")
          unblock(profileId)
        })
        case false if (c.blacklisted.isDefined) => c.blacklisted.get.cancel();
          None
        case false => None
      }, pending = pending))

    }


  }
}


trait FinderStrategy {

  type Apply = PartialFunction[(String, ChannelContext), Unit]

  def apply(trench: Trench): Apply
}

case class DefaultFindStrategy(ctx: BattleField) extends FinderStrategy {
  private val logger = Logger(getClass)

  def apply(trench: Trench): Apply = {
    case (opponentId: String, c: ChannelContext) =>
      ctx.finders ! ApplyToFinder(opponentId, c)


  }
}


