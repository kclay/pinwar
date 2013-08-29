package battle

import akka.actor.{Status, ActorRef, ActorLogging, Actor}
import scala.concurrent.duration._
import scala.Some
import models.{ChallengeToken, War}
import java.util.UUID
import scala.concurrent.Future
import play.api.libs.json.JsValue
import akka.pattern._
import akka.event.LoggingReceive

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/28/13
 * Time: 10:39 PM 
 */

case class WorkProcessor(ctx: BattleField) extends Actor with ActorLogging {

  import ctx._

  import utils.Serialization.Writes.throwable2Json


  import akka.util.Timeout

  implicit val timeout = Timeout(5, SECONDS)


  implicit val ec = system.dispatcher


  def newWar(sender: ActorRef, creatorId: String, opponentId: String) = (master.ask(NewWar(creatorId, opponentId))(Timeout(20, SECONDS))).mapTo[War]

  // map a track name to peers that have entirely streamed it and are still online


  def uid = UUID.randomUUID().toString


  def receive = LoggingReceive {
    case work: Any => doWork(sender, work)
  }

  // Required to be implemented
  def doWork(workSender: ActorRef, work: Any): Unit = Future {
    processWork(workSender, work)

  }


  def push(profileId: String, m: JsValue) = Connection(profileId) ! m


  private def newInvite(profileId: String, email: String) = {
    // check to see there is a pending invite already for the requesting profile
    val previous = invites.find {
      case (k, (creatorId, _)) => creatorId == profileId
    }

    previous match {

      case Some((token, (_, e))) if (email ne e) => None // check to see if the user is trying to send another an invite to another email
      case Some((token, _)) => Some(token) // already send invite to this user
      case _ => {
        val token = uid
        invites += (token ->(profileId, email))

        invitesIds += profileId
        Some(token)
      }
    }

  }

  private def processWork(ref: ActorRef, work: Any): Unit = work match {
    case c: Connect => connections ! c

    case f: Find => finders.tell(f, ref)


    case cr@ChallengeResponse(profileId, token, accepted, creatorId) => {
      log info s"Processing $cr"



      ChallengeToken(token) match {

        case Some(ct) => ct.resolve(profileId, accepted)
        case None => log warning s"Finder for challenge token $token was not found"
          push(profileId, new Error(s"${profileFor(creatorId).name} went offline"))
      }


    }
    case HandleInvite(opponentId, token, accept, profile) => {


      invites.remove(token) match {
        case Some((creatorId, _)) => {
          if (accept) {
            // update status to pending
            trench ! MarkPending(creatorId, opponentId)

            // try to resolve the invite
            newWar(sender, creatorId, opponentId) pipeTo ref

          } else {
            push(creatorId, new Error(s"${profile.name} has declined your challenge"))


          }


        }
        case _ => {
          // creator is offline

          push(opponentId, new Error(s"Your opponent is offline now"))
        }
      }


    }
    case Rematch(profileId, opponentId) => {


      val profile = profileFor(opponentId)
      ref ! (newInvite(profileId, profile.email) map {
        token => RematchContext(token, profile.email, profile)
      } getOrElse new Error(s"Was unable to send out rematch challenge"))


    }
    case Invite(profileId, email) => {


      ref ! newInvite(profileId, email).getOrElse(Status.Failure(new Error(s"You already have an invite pending for ${email}")))


    }

    case msg: ForWars => Wars ! msg


    case Disconnect(profileId) => Trench ! RemoveUser(profileId)
    case _ => log.info("nothing")


  }

}