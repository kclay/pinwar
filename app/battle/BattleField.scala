package battle

import play.api.libs.json._
import play.api.libs.iteratee.Concurrent.Channel
import models._
import akka.actor._
import java.util.UUID
import scala.collection.mutable
import play.api.Logger
import utils.Serialization.Reads._


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 10:49 PM 
 */


class BattleField extends Actor {


  import Schema._

  private val logger = Logger(getClass)

  import context._
  import scala.collection.script._
  import scala.util.{Success, Failure}
  import scala.concurrent.duration._

  import akka.pattern.ask

  type C = Channel[JsValue]

  type CC = ChannelContext
  type Sub = (String, CC)

  case class ChannelContext(channel: C, pending: Boolean = false)

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
      private val p = promise[ActorRef]
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

      def resolve(opponentId: String) = p.completeWith(newWar(sender, creatorId, opponentId))


    }

    def watch(creatorId: String, sender: ActorRef, timeout: Int = 60) = {

      val watcher = Watcher(creatorId, sender, timeout seconds)

      pending.append(watcher)
      watcher.future


    }


    def notify(pub: Trench, event: Message[Sub] with mutable.Undoable) {
      (event match {
        case i: Include[Sub] => Some(i.elem)
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

  import akka.util.Timeout

  implicit val timeout = Timeout(5, SECONDS)

  def newWar(sender: ActorRef, creatorId: String, opponentId: String) = (self ? NewWar(creatorId, opponentId)).mapTo[ActorRef]

  // map a track name to peers that have entirely streamed it and are still online
  val trench = new Trench

  var battles = Map.empty[String, WarBattle]

  val sub = new TrenchSub

  trench.subscribe(sub)

  def uid = UUID.randomUUID().toString

  def receive = {
    case Connect(profileId, channel) =>
      trench + (profileId -> ChannelContext(channel))


    case Find(profileId, timeout) => {

      val ref = sender
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


    case NewWar(creatorId, opponentId) => {
      logger.debug(s"New War request for $creatorId vs $opponentId")
      val maybeWar = (for {
        creator <- trench get creatorId
        opponent <- trench get opponentId

      } yield {
        logger.debug("Found users creating war instance")
        val war = War(uid, creatorId, opponentId)
        val ref = war.save match {
          case Right(i) => {
            val battle = actorOf(Props(new WarBattle(war, creator.channel, opponent.channel)), name = war.id)
            watch(battle)
            Some(battle)

          }
          case _ => None

        }
        ref

      }).flatten
      sender ! maybeWar


    }
    case Disconnect(profileId) =>


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
    case a: WarAction => {
      log.debug(s"Recieved WarAction : $a comming from ${a.profileId}")

      a.action.track(war, a.profileId) match {
        case Right(p) => log.debug(s"Tracked $p")
        case Left(e) => log.debug(s"Unable to track $e")
      }
    }
  }
}

class Lobby extends Actor {
  def receive = ???
}


abstract class CanTrack[T <: WithPoints](implicit m: Manifest[T]) extends BattleAction {

  type TrackType = T

  lazy val mf: Manifest[TrackType] = m


}

sealed trait BattleAction {


  type TrackType <: WithPoints
  val action: String
  type Self = this.type

  import Schema._


  def read(value: JsValue): Option[BattleAction]


  def unapply(value: JsValue) = (value \ "name").asOpt[String] map {
    case n if (n.equals(action)) => read(value)
    case _ => None
  }

  def track(war: War, profileId: String): Either[Error, Points[TrackType]] = {
    val record = factory(war, profileId)


    val ct = this.asInstanceOf[CanTrack[TrackType]]

    implicit val mf = ct.mf

    record.save match {
      case Right(b) => if (b.inserted == 1) Right(Points[TrackType](record.points, record)) else Left(new Error("unable to save"))
      case Left(e) => Left(new Error("Unable to save"))
    }
  }

  protected def factory(war: War, profileId: String): TrackType

}


case class CreateBoard(id: String, name: String, category: Category, url: String) extends CanTrack[Board] {
  self =>


  val action = "create_board"


  def read(value: JsValue) = value.asOpt[CreateBoard]

  def factory(war: War, profileId: String) = Board(id, profileId, name, category, url, 5000)
}


trait PinAction {

  val id: String
  val board: Board

  val images: Seq[Image]

}

case class Repined(id: String, board: Board, images: Seq[Image]) extends CanTrack[Repin] {


  val action = "re_pined"


  def read(value: JsValue) = value.asOpt[Repined]

  protected def factory(war: War, profileId: String) = Repin(id, board.id, profileId, 500)
}

case class CreatePin(id: String, board: Board, images: Seq[Image]) extends CanTrack[Pin] {


  val action = "create_pin"

  def read(value: JsValue) = value.asOpt[CreatePin]

  protected def factory(war: War, profileId: String) = Pin(id, board.id, profileId, 1000)
}

case class Points[T](amount: Int, context: T)

case class Track(war: War)


import play.api.libs.json._
import utils.StringHelper.lowerCaseWithUnderscore


object Extractor {


  abstract class CanBuild[T](implicit rds: Reads[T]) {
    def unapply(value: JsValue): Option[T] = (value \ "event").asOpt[String] match {
      case Some(e) if (e.equals(name)) => rds.reads(value \ "data").fold(
        valid = v => Some(v),
        invalid = e => None
      )
      case _ => None
    }

    lazy val name: String = lowerCaseWithUnderscore(getClass.getSimpleName)
  }

  case object WarAction extends CanBuild[WarAction]


  case object Find extends CanBuild[Find]

  case object Invite extends CanBuild[Invite]

}

trait Event {

  type Self <: Event

  val profileId: String


}


case class Invite(profileId: String, email: Option[String]) extends Event


case class NewWar(profileId: String, opponentId: String)

case class Disconnect(profileId: String)

case class Connect(profileId: String, channel: Channel[JsValue])

case class Find(profileId: String, timeout: Int = 60) extends Event

case class WarAction(profileId: String, war: String, action: BattleAction) extends Event



