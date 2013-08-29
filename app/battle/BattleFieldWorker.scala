package battle

import play.api.libs.json._
import akka.actor._
import java.util.UUID
import scala.concurrent.duration._


import akka.pattern.{ask, pipe}
import scala.concurrent._

import utils.Worker
import scala.Some
import models.War
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

  implicit val ctxSystem = context.system
  implicit val ec = ctxSystem.dispatcher


  def newWar(sender: ActorRef, creatorId: String, opponentId: String) = (master.ask(NewWar(creatorId, opponentId))(Timeout(20, SECONDS))).mapTo[War]

  // map a track name to peers that have entirely streamed it and are still online


  def uid = UUID.randomUUID().toString


  // Required to be implemented
  def doWork(workSender: ActorRef, work: Any): Unit = Future {
    processWork(workSender, work)
    WorkComplete("done")
  } pipeTo (self)


  def push(profileId: String, m: JsValue) = ctxFor(profileId) map (_ ! m)

  def ctxFor(profileId: String) = trench get (profileId)

  def profileFor(profileId: String) = caches.profiles get profileId


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


  override def actorTerminated(actor: ActorRef) {
    super.actorTerminated(actor)
    actor.path.name match {
      case ProfileId(profileId) => {
        context.unwatch(actor)
        handleDisconnect(profileId)
      }
      case _ => log error s"Actor terimated but wasn't profile $actor"
    }
  }

  private def processWork(ref: ActorRef, work: Any): Unit = work match {
    case Connect(profileId, channel, fromInvite) => {
      val connection = context.system.actorOf(Props(Connection(channel)), name = s"profile_${profileId}")
      context.watch(connection)
      trench += (profileId -> new ChannelContext(connection.path, pending = fromInvite))
    }


    case Find(profileId) => {

      pendingFinders.find(_.creatorId == profileId) match {
        case Some(_) => push(profileId, new Error("You already have a pending request active, wait till it expires."))
        case _ => {
          // update state
          trench :=+ profileId

          val profile = profileFor(profileId)

          val finder = find(profile, ref, findTimeout.duration)

          pendingFinders.find(f => f.state == Waiting && f.creatorId != profileId) match {
            case Some(f) => f.resolve(profileId, true)
            case _ => trench.find {
              case (p, c) => p != profileId && c.available
            } match {
              case Some((opponentId, _)) => {


                log.info(s"Found a opponent for ${profile.name} to battle ${profileFor(opponentId).name}")

                finder.request(opponentId)
              }
              case _ => log.info(s"Couldn't find any available user, putting ${profile.name} into `listen` state")


            }

          }


          finder.future pipeTo ref
        }
      }


    }

    case cr@ChallengeResponse(profileId, token, accepted, creatorId) => {
      log info s"Processing $cr"
      challengeTokens remove (token) match {
        case Some(finder) => finder resolve(profileId, accepted)
        case _ => {
          log warning s"Finder for challenge token $token was not found"
          push(profileId, new Error(s"${profileFor(creatorId).name} went offline"))
        }

      }
    }
    case HandleInvite(opponentId, token, accept, profile) => {


      invites.remove(token) match {
        case Some((creatorId, _)) => {
          if (accept) {
            // update status to pending
            trench :=+ creatorId
            trench :=+ opponentId
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
      val maybeWar = (for {
        creator <- trench get creatorId
        opponent <- trench get opponentId

      } yield {
        log.info("Found users creating war instance")
        val war = War.create(creatorId, opponentId)
        war map {
          w =>

          // NEW
            trench :=+ creatorId
            trench :=+ opponentId
            context.system.actorOf(Props(new WarBattle(w, creatorId, opponentId, creator.actorPath, opponent.actorPath)), name = s"war_${w.id.get}")



            invitesIds -= opponentId
            invitesIds -= creatorId
            pendingFinders.filter(f => f.creatorId == creatorId || f.creatorId == opponentId).foreach(_.destroy)
            //watch(battle)
            w

        }


      }).flatten

      maybeWar match {
        case Some(w) => ref ! w
        case _ => ref ! Failure(new Error("Opponent wasn't found"))
      }


    }


    case wa: WarAction => {
      log info s"Passing WarAction $wa"

      val battle = battleRef(wa.war)

      battle.tell(wa, ref)

    }

    case Disconnect(profileId) => handleDisconnect(profileId)


    case _ => log.info("nothing")


  }

  private def handleDisconnect(profileId: String) = trench.remove(profileId) map {
    cc => {
      log.info(s"Sending PoisonPill to $profileId")
      cc ! PoisonPill
      invitesIds -= profileId

      Seq(finders, pendingFinders).map {
        collection => collection.find(_.creatorId == profileId).map {
          f => {
            log info s"Destorying finder for $profileId"
            f.destroy

          }
        }
      }

      challengeTokens retain ((t, f) => f.creatorId != profileId)
      invites.retain {
        case (k, (creatorId, _)) => creatorId != profileId
      }
    }
  }


}
