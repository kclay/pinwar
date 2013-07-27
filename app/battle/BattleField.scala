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


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 10:49 PM 
 */
case class ChannelContext(channel: Channel[JsValue], pending: Boolean = false, blacklisted: Option[Cancellable] = None) {


  def available = !pending && blacklisted.isEmpty

  def destroy = blacklisted map (_.cancel())

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


  class CacheStore {

    case class InternalCache[T](prefix: String = "", filler: String => T, private val expire: Int = 0)(implicit ct: ClassTag[T]) {
      private def p(key: String) = s"${prefix}_${key.toString}"

      def get(key: String): T = Cache.getOrElse[T](p(key)) {
        filler(key)
      }

      def as[S](key: String)(implicit ct: ClassTag[S]) = Cache.getAs[S](key)

      def set(key: String, value: T, expiration: Int = expire) = Cache.set(p(key), value, expiration)

      def apply(key: String): Option[T] = as[T](key)

      def apply(key: String, value: T) = set(key, value)

      def -(key: String) = Cache


    }

    lazy val profiles = new InternalCache[Profile]("profile", (id => Schema.profiles.get(id).run.right.get))

    lazy val invites = new InternalCache[String]("invite", (id => null), 30.minutes.toSeconds.toInt)


  }

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
  val challengeTokens = mutable.Map.empty[String, Finder]
  val pendingFinders = mutable.ArrayBuffer.empty[Finder]


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

  def battleRef(id: String) = system.actorFor(actorPath(id))

  def worker(name: String) = system.actorOf(Props(
    new BattleFieldWorker(actorPath("battle_field"))), name = name)

  private val numOfWorkers = 1
  lazy val master = {
    val _master = system.actorOf(Props[Master], name = "battle_field")
    (0 to numOfWorkers).foreach(x => worker(s"worker_${x + 1}"))
    _master
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


  def newWar(sender: ActorRef, creatorId: String, opponentId: String) = (master.ask(NewWar(creatorId, opponentId))(Timeout(20, SECONDS))).mapTo[War]

  // map a track name to peers that have entirely streamed it and are still online


  def uid = UUID.randomUUID().toString


  // Required to be implemented
  def doWork(workSender: ActorRef, work: Any): Unit = Future {
    processWork(workSender, work)
    WorkComplete("done")
  } pipeTo (self)


  def push(profileId: String, m: JsValue) = ctxFor(profileId) map (_.channel push (m))

  def ctxFor(profileId: String) = trench get (profileId)

  def profileFor(profileId: String) = caches.profiles get profileId


  private def processWork(ref: ActorRef, work: Any): Unit = work match {
    case Connect(profileId, channel) => {
      trench += (profileId -> ChannelContext(channel))
    }


    case Find(profileId) => {


      // update state
      trench :=+ profileId
      val profile = profileFor(profileId)
      val finder = find(profile, ref, findTimeout.duration)
      trench.collectFirst {
        case (p, c) if (p != profileId && c.available) => p
      } match {
        case Some(opponentId) => {
          val c = profileFor(opponentId)

          log.info(s"Found a opponent for ${profile.name} to battle ${profileFor(opponentId).name}")

          finder.request(opponentId)
        }
        case _ => log.info(s"Couldn't find any available user, putting ${profile.name} into `listen` state")


      }


      finder.future pipeTo ref

    }

    case ChallengeResponse(profileId, token, accepted, creatorId) => {
      challengeTokens remove (token) match {
        case Some(finder) => finder resolve(profileId, accepted)
        case _ => push(profileId, new Error(s"${profileFor(creatorId).name} went offline"))

      }
    }
    case HandleInvite(opponentId, creatorId, token, accept, profile) => {


      invites.remove(token) match {
        case Some((creatorId, email)) => {
          if (accept) {
            // update status to pending
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

      ref ! token.getOrElse(Status.Failure(new Error(s"You already have an invite pending for ")))


    }

    case NewWar(creatorId, opponentId) => {
      log.info(s"New War request for $creatorId vs $opponentId")
      val maybeWar = (for {
        creator <- trench get creatorId
        opponent <- trench get opponentId

      } yield {
        log.info("Found users creating war instance")
        val war = War(uid, creatorId, opponentId)
        val value = war.save match {
          case Right(i) => {
            val battle = actorOf(Props(new WarBattle(war, creator.channel, opponent.channel)), name = war.id)

            invitesIds -= opponentId
            invitesIds -= creatorId
            watch(battle)
            Some(war)

          }
          case _ => None

        }
        value

      }).flatten

      maybeWar match {
        case Some(w) => ref ! w
        case _ => ref ! Failure(new Error("Opponent wasn't found"))
      }


    }
    case wa: WarAction => {
      battleRef(wa.war).tell(wa, ref)
    }

    case Disconnect(profileId) => {
      trench.remove(profileId)
      invitesIds -= profileId
      pendingFinders.find(_.creatorId == profileId).map {
        f => {
          f.destroy
          val index = pendingFinders.indexOf(f)
          if (index != -1)
            pendingFinders.remove(index)
        }
      }
      challengeTokens retain ((t, f) => f.creatorId != profileId)
      invites.retain((k, v) => v != profileId)
    }
    case _ => log.info("nothing")


  }


}


class WarBattle(war: War, creator: Channel[JsValue], opponent: Channel[JsValue]) extends Actor {

  import akka.event.Logging
  import utils.Serialization.Writes._

  private lazy val log = Logging(context.system, this)
  val channels = Seq(creator, opponent)


  def chanFor(profileId: String) = if (war.creatorId == profileId) creator else opponent

  override def preStart() {
    super.preStart()
  }
  implicit def pointsWrites[T](points: Points[T])(implicit wsj: Writes[T]) = {
    Json.obj(
      "event" -> "points",
      "amount" -> points.amount,
      "profileId" -> points.profileId,
      "context" -> wsj.writes(points.context)
    )
  }

  def receive = {
    case a@WarAction(profileId, warId, action) => {
      log.debug(s"Recieved WarAction : $a coming from ${profileId}")


      action.track(war, profileId) match {
        case Right(p) => {
          log.debug(s"Tracked $p")
         // channels.foreach(_.push(p))
        }
        case Left(e) => log.debug(s"Unable to track $e")
      }
    }
  }
}







