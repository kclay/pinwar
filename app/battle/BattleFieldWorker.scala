package battle

import play.api.libs.json._
import akka.actor._
import java.util.UUID
import scala.concurrent.duration._


import akka.pattern.{ask, pipe}
import scala.concurrent._

import utils.Worker
import scala.Some
import models.{ChallengeToken, War}
import scala.util.Try

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/7/13
 * Time: 7:09 PM 
 */
class BattleFieldWorker(ctx: BattleField, masterPath: ActorPath) extends Worker(masterPath) {

  import ctx._

  import utils.Serialization.Writes.throwable2Json


  import scala.util.Failure


  import akka.util.Timeout

  implicit val timeout = Timeout(5, SECONDS)


  implicit val ec = system.dispatcher


  def newWar(sender: ActorRef, creatorId: String, opponentId: String) = (master.ask(NewWar(creatorId, opponentId))(Timeout(20, SECONDS))).mapTo[War]

  // map a track name to peers that have entirely streamed it and are still online


  def uid = UUID.randomUUID().toString


  // Required to be implemented
  def doWork(workSender: ActorRef, work: Any): Unit = Future {
    processWork(workSender, work)
    WorkComplete("done")
  } pipeTo (self)


  def push(profileId: String, m: JsValue) = Connection.actorFor(profileId) ! m


  val ProfileId = "profile_([0-9]+)".r


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

    case NewWar(creatorId, opponentId) => {
      log.info(s"New War request for $creatorId vs $opponentId")

      val creator = Connection.actorFor(creatorId)
      val opponent = Connection.actorFor(opponentId)

      log.info("Found users creating war instance")
      val war = War.create(creatorId, opponentId)
      war map {
        w =>

          context.system.actorOf(Props(new WarBattle(w, creatorId, opponentId, creator, opponent)), name = s"war_${w.id.get}")



          invitesIds -= opponentId
          invitesIds -= creatorId
          //watch(battle)
          w

      }




      war match {
        case Some(w) => ref ! w
        case _ => ref ! Failure(new Error("Opponent wasn't found"))
      }


    }


    case wa: WarAction => {
      log info s"Passing WarAction $wa"

      val battle = battleRef(wa.war)

      battle.tell(wa, ref)

    }

    case d@Disconnect(profileId) => {


    }
    case _ => log.info("nothing")


  }


}
