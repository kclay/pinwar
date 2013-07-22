package battle

import play.api.libs.json._
import play.api.libs.iteratee.Concurrent.Channel
import models._
import akka.actor._
import java.util.UUID
import scala.collection.mutable
import play.api.Logger
import scala.Some
import models.War
import scala.util.Try
import scala.collection.mutable.ArrayBuffer
import scala.collection.script.{Update, Include, Message}
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import akka.util.Timeout


import akka.pattern.{ask, pipe}
import scala.concurrent.{Future, ExecutionContext}

import utils.{Worker, Master}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 10:49 PM 
 */


object BattleField {

  import scala.concurrent.duration._
  import play.api.Play.current


  val system = Akka.system
  private implicit val ec: ExecutionContext = system.dispatcher


  def actorPath(name: String) = (ActorPath.fromString(
    "akka://%s/user/%s".format(system.name, name)))

  def battleRef(id: String) = system.actorFor(actorPath(id))

  def worker(name: String) = system.actorOf(Props(
    new BattleFieldWorker(actorPath(name))))

  private val numOfWorkers = 1
  lazy val master = {
    val _master = system.actorOf(Props[Master], name = "battle_field")
    (0 to numOfWorkers).foreach(x => worker("battle_field"))
    _master
  }

  private val logger = Logger(getClass)
  type C = Channel[JsValue]

  type CC = ChannelContext
  type Sub = (String, CC)


  case class ChannelContext(channel: C, pending: Boolean = false)

  val trench = new Trench

  val battles = Map.empty[String, WarBattle]

  val sub = new TrenchSub

  val invites = mutable.Map.empty[String, String]
  val invitesIds = ArrayBuffer.empty[String]

  trench.subscribe(sub)


  class Trench extends mutable.HashMap[String, CC] with mutable.ObservableMap[String, CC] {
    type Pub = Trench

    def :=+(profileId: String) = context(profileId, true)

    def :=-(profileId: String) = context(profileId, false)

    def context(profileId: String, pending: Boolean) = this += (profileId -> trench.get(profileId).get.copy(pending = pending))
  }


  class TrenchSub extends mutable.Subscriber[Message[Sub] with mutable.Undoable, Trench] {


    import scala.concurrent._

    //Use the system's dispatcher as ExecutionContext


    val pending = mutable.ArrayBuffer.empty[Watcher]

    case class WatchTimeout(creatorId: String) extends Exception(s"Wasn't able to find any opponents for ${creatorId}")

    case class Watcher(creatorId: String, sender: ActorRef, timeout: FiniteDuration = 60 seconds) {
      private val p = promise[War]
      private val INTERVAL = 5


      private val ctx = trench.get(creatorId).get
      var passed = 0
      private val countdown = system.scheduler.schedule(0 seconds, INTERVAL seconds)(() => {
        passed += INTERVAL
        ctx.channel.push(
          Json.obj(
            "event" -> "countdown",
            "data" -> Json.obj(
              "time" -> passed
            )
          ))

      })
      private val handle: Cancellable = system.scheduler.scheduleOnce(timeout + (2 seconds))(() => {
        countdown.cancel()
        pending.remove(pending.indexOf(this))
        p.failure(WatchTimeout(creatorId))

      })

      def future = p.future

      future onSuccess {
        case _ => {
          countdown.cancel()
          handle.cancel()
        }
      }


      def resolve(opponentId: String) = p.completeWith(master.ask(NewWar(creatorId, opponentId))(Timeout(20 seconds)).mapTo[War])


    }

    def watch(creatorId: String, sender: ActorRef, timeout: Int = 60) = {

      val watcher = Watcher(creatorId, sender, timeout seconds)
      pending.append(watcher)
      watcher.future


    }


    def notify(pub: Trench, event: Message[Sub] with mutable.Undoable) {
      (event match {
        case i: Include[Sub] if (!invitesIds.contains(i.elem._1)) => Some(i.elem)
        case u: Update[Sub] if (!u.elem._2.pending) => Some(u.elem)
        case _ => None
      }) map {

        case (opponentId: String, _: C) => {
          pending.headOption map {
            watcher => {

              logger.info("Found a pending users, removing from queue and resovling Watcher")
              pending.remove(0)
              // update opponent state
              pub :=+ opponentId
              watcher resolve opponentId

            }
          } orElse {
            logger.info("No pending users waiting")
            None
          }


        }
        case _ =>
      }
    }


  }

}


class BattleFieldWorker(masterPath: ActorPath) extends Worker(masterPath) {

  import BattleField._

  import Schema._

  private val logger = Logger(getClass)

  import context._

  import scala.util.{Success, Failure}


  import akka.util.Timeout

  implicit val timeout = Timeout(5, SECONDS)


  def newWar(sender: ActorRef, creatorId: String, opponentId: String) = (self.ask(NewWar(creatorId, opponentId))(Timeout(20, SECONDS))).mapTo[War]

  // map a track name to peers that have entirely streamed it and are still online


  def uid = UUID.randomUUID().toString


  // Required to be implemented
  def doWork(workSender: ActorRef, work: Any): Unit = Future {
    processWork(workSender, work)
    WorkComplete("done")
  } pipeTo (self)


  private def processWork(ref: ActorRef, work: Any): Unit = work match {
    case Connect(profileId, channel) => {
      trench += (profileId -> ChannelContext(channel))
    }


    case Find(profileId, timeout) => {


      // update state
      trench :=+ profileId
      val f = (trench.headOption.filter {
        case (p, c) => p != profileId && !c.pending
      }.headOption map {
        case (opponentId, _) => newWar(ref, profileId, opponentId)
      }).getOrElse(sub.watch(profileId, ref, timeout))

      f onComplete {
        case Success(w) => ref ! w
        case Failure(e) =>
      }
    }

    case HandleInvite(opponentId, token, accept, profile) =>

      // update status to pending
      trench :=+ opponentId
      // try to resolve the invite

      Try(newWar(sender, invites.remove(token).get, opponentId)) match {
        case Success(w) => w pipeTo (ref)
        case Failure(e) => ref ! Failure(e)
      }

    case Invite(profileId, email) => {
      // check to see there is a pending invite already for the requesting profile
      val token = invites.find {
        case (k, v) => v == profileId
      } map (_._1) getOrElse {
        val token = uid
        invites += (token -> profileId)

        invitesIds += profileId
        token
      }


      ref ! token
    }

    case NewWar(creatorId, opponentId) => {
      logger.info(s"New War request for $creatorId vs $opponentId")
      val maybeWar = (for {
        creator <- trench get creatorId
        opponent <- trench get opponentId

      } yield {
        logger.info("Found users creating war instance")
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
      invites.retain((k, v) => v != profileId)
    }
    case _ => logger.info("nothing")


  }


}

class WarBattle(war: War, creator: Channel[JsValue], opponent: Channel[JsValue]) extends Actor {

  import akka.event.Logging

  private lazy val log = Logging(context.system, this)
  val channels = Seq(creator, opponent)


  def chanFor(profileId: String) = if (war.creatorId == profileId) creator else opponent

  override def preStart() {
    super.preStart()
  }

  def receive = {
    case a@WarAction(profileId, warId, action) => {
      log.debug(s"Recieved WarAction : $a coming from ${profileId}")

      action.track(war, profileId) match {
        case Right(p) => log.debug(s"Tracked $p")
        case Left(e) => log.debug(s"Unable to track $e")
      }
    }
  }
}







