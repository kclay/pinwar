package battle

import models.{CacheStore, War}
import akka.actor._
import scala.concurrent._
import akka.util.Timeout
import scala.concurrent.duration._
import play.api.libs.json.JsValue
import scala.collection.mutable
import akka.pattern.ask
import utils.{TestAble, ActorCreator}
import play.api.Mode._
import scala.Some
import akka.actor.Terminated
import models.Profile
import akka.event.LoggingReceive


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/23/13
 * Time: 7:34 PM 
 */


sealed trait FinderState

case object InFlight extends FinderState

case object Waiting extends FinderState

case object Done extends FinderState

case object RequestFind

case class QueueFinder(ref: ActorRef)

case class DestroyFinder(profileId: String)


case class ResolveChallenge(profileId: String, opponentId: String, accepted: Boolean)

case class ChallengeRequested(opponentId: String)

case class ApplyToFinder(opponentId: String, channel: ChannelContext)

case object SeenWho


case class Seen(list: Seq[String])


object Finders extends ActorCreator {


  def props(bf: BattleField) = Props(classOf[Finders], bf.trench, bf.findTimeout, bf.caches, bf.currentMode)

}

import scala.concurrent.stm._

case class FinderScope(ref: ActorRef, profileId: String, state: Ref[FinderState])

case class Finders(trench: ActorRef, timeout: Timeout, cache: CacheStore, mode: play.api.Mode.Mode) extends Actor with ActorLogging {


  val pending = mutable.ListBuffer.empty[FinderScope]


  val stashed = mutable.ListBuffer.empty[FinderScope]


  def newFinder(profileId: String) = {
    val profile = cache.profiles get profileId


    pending find (_.state.single.get == Waiting) match {
      case Some(scope) => self ! ResolveChallenge(scope.profileId, profileId, true)
      case _ => {
        val state = Ref[FinderState](Waiting)
        val finder = context.actorOf(Props(classOf[Finder], trench, profile, timeout, state), profileId)
        // val finder = Finder(trench, profile, timeout, mode)
        context.watch(finder)
        pending += FinderScope(finder, profileId, state)


      }
    }


  }

  def receive = LoggingReceive {

    case Find(profileId) => newFinder(profileId)


    case rc@ResolveChallenge(profileId, opponentId, resolved) => {
      stashed.find(_.profileId == profileId).foreach {
        s =>
          if (!resolved) {
            stashed -= s
            pending += s
          }
          s.ref ! rc
      }
    }

    case ApplyToFinder(opponentId: String, _) => pending.headOption match {
      case Some(finder) => {
        log.info("Found a pending users, removing from queue and resolving Finder")
        pending remove 0
        stashed += finder
        // update opponent state
        finder.ref ! ChallengeRequested(opponentId)

      }
      case _ => log.warning("No pending users waiting")

    }


    case Terminated(ref) => Seq(pending, stashed).foreach {
      collection =>
        val index = collection.map(_.ref).indexOf(ref)
        if (index > -1) collection.remove(index)
    }
    case QueueFinder(ref) =>
      // pending += ref
      stashed.find(_.ref == ref) match {
        case Some(s) =>
          stashed -= s
          pending += s
        case _ => log info s"Finder already in queue $ref"
      }

    case DestroyFinder(profileId) => {
      context.child(profileId) match {
        case Some(ref) =>
          ref ! PoisonPill
          log debug s"Send PoisonPill to $profileId"
        case _ => log warning (s"No finder found for $profileId")
      }


    }
  }

}

case class FinderTimeout(creatorId: String) extends Exception(s"Wasn't able to find any opponents for ${creatorId}")


object Finder extends ActorCreator {


  def apply(trench: ActorRef, profile: Profile, timeout: Timeout, mode: M)(implicit system: ActorSystem): ActorRef = apply(Props(classOf[Finder], trench, profile, timeout), mode, s"finders/${profile.id}")


  def props(bf: BattleField) = ???
}


case class Finder(trench: ActorRef, profile: Profile, timeout: Timeout, state: Ref[FinderState]) extends Actor with TestAble {

  import utils.Serialization.Writes._


  implicit val system = context.system

  val scheduler = system.scheduler

  val master = system.actorSelection("/user/battleField")
  private val alreadySeen = collection.mutable.ArrayBuffer.empty[String]

  def seen(profileId: String) = alreadySeen.contains(profileId)

  implicit val exec: ExecutionContext = system.dispatcher

  private val p = promise[War]

  private val INTERVAL = 5

  val creatorId = profile.id

  override def preStart() {
    super.preStart()


    trench ! FindOpponent(creatorId, alreadySeen, Some((self, profile)))
    changeState(Waiting)
  }


  private def changeState(newState: FinderState) = atomic {
    implicit txn =>
      state() = newState
  }

  override def postStop() {
    destroy
    super.postStop()
  }

  def destroy = {

    countdown.cancel()
    handle.cancel()

  }

  private val connection = Connection(creatorId)


  var passed = 0
  private val countdown = scheduler.schedule(INTERVAL seconds, INTERVAL seconds) {
    passed += INTERVAL
    val msg: JsValue = Countdown(passed)
    connection ! msg


  }


  private val handle: Cancellable = scheduler.scheduleOnce(timeout.duration) {
    destroy


    p.tryFailure(FinderTimeout(creatorId))

  }

  def future = p.future

  future onComplete {
    case _ => destroy
  }


  def receive = LoggingReceive {

    case ChallengeRequested(opponentId) => {
      trench ! RequestChallenge(self, profile, opponentId)
      changeState(InFlight)
    }
    case ResolveChallenge(_, opponentId, accepted) => resolve(opponentId, accepted)
  }

  def resolve(opponentId: String, accepted: Boolean): Unit = if (accepted) {


    future onFailure {
      case x: Throwable => println(x)
    }
    p.completeWith(master.ask(NewWar(creatorId, opponentId))(Timeout(20 seconds)).mapTo[War])
    changeState(Done)
  } else {
    alreadySeen += opponentId

    val profileId = profile.id

    trench ! FindOpponent(profileId, alreadySeen, Some((self, profile)))
    changeState(Waiting)


    trench ! Block(opponentId) // make opponent not in pending state


  }

  /*
  def resolve(opponentId: String, accepted: Boolean): Unit = {

    if (accepted) {

      /* future onSuccess {
         case war: War => {
           val profiles = Seq(creatorId, opponentId)
           val msg: JsValue = profiles.map(ctx.caches.profiles get _) match {
             case Seq(c, o) => WarAccepted(c, o, war)
           }
           val ctxs = profiles.map(ctx.trench.get(_)).filter(_.isDefined)
           if (ctxs.size != 2) {
             // someone left
             ctx.pendingFinders += this

           } else {

             ctxs.flatten.foreach(_ ! msg)
           }


         }
       } */
      future onFailure {
        case x: Throwable => println(x)
      }
      p.completeWith(ctx.master.ask(NewWar(creatorId, opponentId))(Timeout(20 seconds)).mapTo[War])

    } else {
      alreadySeen += opponentId

      val profileId = profile.id
      ctx.trench.collectFirst {
        case (p, c) if (p != profileId && c.available && !alreadySeen.contains(p)) => p
      } match {
        case Some(opponentId) => {

          request(opponentId)
        }
        case _ => ctx.pendingFinders += this // re add to queue
      }
      ctx.trench block opponentId // make opponent not in pending state


    }
  }
        */

}
