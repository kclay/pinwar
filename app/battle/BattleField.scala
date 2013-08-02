package battle

import play.api.libs.json._
import play.api.libs.iteratee.Concurrent.Channel
import models._
import akka.actor._
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._


import akka.pattern.{ask, pipe}
import scala.concurrent._

import utils.{Worker, Master}
import play.api.cache.{EhCachePlugin, CachePlugin, Cache}
import play.api.Play.current
import akka.util.Timeout
import models.Schema._
import scala.Some
import models.War
import net.sf.ehcache.event.CacheEventListener
import net.sf.ehcache.{Element, Ehcache}
import scala.reflect.ClassTag
import scala.util.Try


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 10:49 PM 
 */
case class ChannelContext(actorPath: ActorPath, pending: Boolean = false, blacklisted: Option[Cancellable] = None) {

  def !(message: JsValue)(implicit system: ActorSystem) = system.actorFor(actorPath) ! message

  def !(message: AnyRef)(implicit system: ActorSystem) = system.actorFor(actorPath) ! message

  def available = !pending && blacklisted.isEmpty

  def destroy = blacklisted map (_.cancel())

}

object BattleConfig {
  lazy val pointsNeededToWin = play.api.Play.configuration(play.api.Play.current).getInt("points.toWin").get
}

object BattleField {

  import play.api.Play.current


  val instance = new BattleField

  lazy val caches = instance.caches


  current.plugin[CachePlugin] match {
    case Some(p) => {
      val cache = p.asInstanceOf[EhCachePlugin].cache
      cache.getCacheEventNotificationService.registerListener(new CacheEventListener() {
        def notifyElementRemoved(p1: Ehcache, p2: Element) {}

        def notifyElementPut(p1: Ehcache, p2: Element) {}

        def notifyElementUpdated(p1: Ehcache, p2: Element) {}

        def notifyElementExpired(p1: Ehcache, p2: Element) {

          if (p2.getKey.toString.startsWith("invite_")) {
            instance.invites.remove(p2.getObjectValue.asInstanceOf[String])
          }
        }

        def notifyElementEvicted(p1: Ehcache, p2: Element) {}

        def notifyRemoveAll(p1: Ehcache) {}

        def dispose() {}
      })
    }
    case _ =>
  }

}

class BattleField {




  lazy val caches = new CacheStore
  val timeoutThreshold = 5
  val findTimeout = Timeout((60 * 2) + timeoutThreshold, SECONDS)


  val inviteTimeout = Timeout(5, MINUTES)
  val system = Akka.system

  val sub = new TrenchSub(this)

  type ProfileId = String
  type Email = String

  val invites = mutable.Map.empty[String, (ProfileId, Email)]
  val invitesIds = ArrayBuffer.empty[String]

  val activeWars = mutable.Map.empty[String, String]

  val challengeTokens = mutable.Map.empty[String, Finder]
  val pendingFinders = mutable.ArrayBuffer.empty[Finder]


  val finders = mutable.ArrayBuffer.empty[Finder]


  val trench = new Trench(this)

  trench.subscribe(sub)

  def challengeTokenFor(f: Finder) = {

    val token = UUID.randomUUID().toString
    challengeTokens += (token -> f)
    token
  }

  def find(creator: Profile, sender: ActorRef, timeout: Timeout) = {


    val finder = Finder(this, creator, sender, timeout)
    pendingFinders.append(finder)
    finder


  }


  def actorPath(name: String) = (ActorPath.fromString(
    "akka://%s/user/%s".format(system.name, name)))


  def battleRef(id: String) = system.actorFor(actorPath(s"war_$id"))

  def worker(name: String) = system.actorOf(Props(
    new BattleFieldWorker(actorPath("battle_field"))), name = name)

  private val numOfWorkers = 1
  lazy val master = {
    val _master = system.actorOf(Props[Master], name = "battle_field")
    (0 to numOfWorkers).foreach(x => worker(s"worker_${x + 1}"))
    _master
  }


}


case class Connection(channel: Channel[JsValue]) extends Actor with ActorLogging {

  override def unhandled(message: Any) {
    super.unhandled(message)
  }

  def receive = {
    case item: JsValue => channel.push(item)

    case x: Any => log info x.toString
    case _ =>
  }
}

class BattleFieldWorker(masterPath: ActorPath) extends Worker(masterPath) {

  import BattleField.{caches, instance}
  import instance._
  import utils.Serialization.Writes.throwable2Json

  import Schema._


  import context._

  import scala.util.Failure


  import akka.util.Timeout

  implicit val timeout = Timeout(5, SECONDS)

  implicit val ctxSystem = context.system


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

  private def processWork(ref: ActorRef, work: Any): Unit = work match {
    case Connect(profileId, channel, fromInvite) => {
      val connection = context.system.actorOf(Props(Connection(channel)), name = s"profile_${profileId}")
      watch(connection)
      trench += (profileId -> ChannelContext(connection.path, pending = fromInvite))
    }


    case Terminated(actor) => {
      actor.path.name match {
        case ProfileId(profileId) => {
          unwatch(actor)
          self ! Disconnect(profileId)
        }
        case _ =>
      }


    }
    case Find(profileId) => {


      // update state
      trench :=+ profileId
      Try(profileFor(profileId)) match {
        case Failure(e) => println(e)
        case _ =>
      }
      val profile = profileFor(profileId)
      val finder = find(profile, ref, findTimeout.duration)
      trench.collectFirst {
        case (p, c) if (p != profileId && c.available) => p
      } match {
        case Some(opponentId) => {


          log.info(s"Found a opponent for ${profile.name} to battle ${profileFor(opponentId).name}")

          finder.request(opponentId)
        }
        case _ => log.info(s"Couldn't find any available user, putting ${profile.name} into `listen` state")


      }


      finder.future pipeTo ref

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
    case HandleInvite(opponentId, creatorId, token, accept, profile) => {


      invites.remove(token) match {
        case Some((creatorId, email)) => {
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

          push(opponentId, new Error(s"${profileFor(creatorId).name} is offline now"))
        }
      }


    }

    case Invite(profileId, email) => {

      // check to see there is a pending invite already for the requesting profile
      val token = invites.find {
        case (k, v) => v == profileId
      } match {

        case Some((i, e)) if (email ne e) => None // check to see if the user is trying to send another an invite to another email
        case Some((i, e)) => Some(i) // already send invite to this user
        case _ => {
          val token = uid
          invites += (token ->(profileId, email))

          invitesIds += profileId
          Some(token)
        }
      }

      ref ! token.getOrElse(Status.Failure(new Error(s"You already have an invite pending for ${email}")))


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

            context.system.actorOf(Props(new WarBattle(w, creatorId, opponentId, creator.actorPath, opponent.actorPath)), name = s"war_${w.id.get}")



            invitesIds -= opponentId
            invitesIds -= creatorId
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

    case d@Disconnect(profileId) => {
      trench.remove(profileId) map {
        c => {
          log.info(s"Sending PoisonPill to $profileId")
          c ! PoisonPill
          invitesIds -= profileId

          Seq(finders, pendingFinders).map {
            c => c.find(_.creatorId == profileId).map {
              f => {
                log info s"Destorying finder for $profileId"
                f.destroy
                val index = c.indexOf(f)
                if (index != -1)
                  c.remove(index)
              }
            }
          }

          challengeTokens retain ((t, f) => f.creatorId != profileId)
          invites.retain((k, v) => v != profileId)
        }

      }

    }
    case _ => log.info("nothing")


  }


}


class WarBattle(war: War, creatorId: String, opponentId: String, creatorPath: ActorPath, opponentPath: ActorPath) extends Actor with ActorLogging {

  import context._

  import utils.Serialization.Writes.{pointsWrites, throwable2Json, appError2Json}

  import com.rethinkscala.Implicits._

  import BattleField.instance.{activeWars, caches}

  val creator = context.system.actorFor(creatorPath)

  val opponent = context.system.actorFor(opponentPath)
  val channels = Seq(creator, opponent)

  val pointsNeededToWin = BattleConfig.pointsNeededToWin
  var creatorPoints = 0
  var opponentPoints = 0


  def countBattle(id: String) = stats.get(id).update(s => s \ "battles" add 1) run

  override def preStart() {
    super.preStart()
    watch(creator)
    watch(opponent)
    //  activeWars += (creatorId -> war.id)
    //activeWars += (opponentId -> war.id)

    countBattle(creatorId)
    countBattle(opponentId)
    log.info(s"Started War = ${war.id}")
  }


  def notifyDisconnect(profileId: String) = {
    val profile = caches.profiles.get(profileId)
    val msg: JsValue = DisconnectError(profile)


    channels foreach (_ ! msg)
    self ! PoisonPill

  }


  def creatorContext = (creatorId, creatorPoints)

  def opponentContext = (opponentId, opponentPoints)

  def contexts = Seq(creatorContext, opponentContext)

  override def postStop() {
    super.postStop()
    unwatch(creator)
    unwatch(opponent)
    //activeWars remove creatorId
    //activeWars remove opponentId
  }


  def checkPoints = {

    val mayWin = ((creatorPoints >= pointsNeededToWin), (opponentPoints >= pointsNeededToWin)) match {
      case (true, _) => Some(creatorId, opponentId)
      case (_, true) => Some(opponentId, creatorId)
      case _ => None

    }
    mayWin map {
      case (won, lose) => {
        stats.get(won).update(s => s \ "wins" add 1) run

        stats.get(lose).update(s => s \ "loses" add 1) run
      }
    }

  }

  def ctxFor(profileId: String) = if (creatorId == profileId) creator else opponent

  def receive = {
    case Terminated(r) if (r == creator || r == opponent) => notifyDisconnect(if (r == creator) creatorId else opponentId)
    case a@WarAction(profileId, warId, action) => {
      log.debug(s"Recieved WarAction : $a coming from ${profileId}")



      action.asInstanceOf[CanTrack[PointContext]].track(war, profileId) match {
        case Right(p) => {
          log.debug(s"Tracked $p")



          if (profileId == creatorId) creatorPoints += p.amount else opponentPoints += p.amount


          val msg: JsValue = pointsWrites.writes(p)

          channels.foreach(_ ! msg)
          checkPoints
        }
        case Left(e) => {
          val msg: JsValue = e
          ctxFor(profileId) ! msg
        }
      }
    }
  }
}


abstract class AppError(val kind: String) {
  val message: String
}


case class DisconnectError(profile: Profile) extends AppError("disconnect") {
  lazy val message = s"${profile.name} has disconnected"
}






