package battle

import models.{CacheStore, War, Profile}
import akka.actor._
import scala.concurrent._
import akka.util.Timeout
import scala.concurrent.duration._
import play.api.libs.json.JsValue
import scala.collection.mutable
import scala.Some
import akka.pattern.ask
import akka.routing.FromConfig
import utils.ActorCreator


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/23/13
 * Time: 7:34 PM 
 */


case object RequestFind

case class QueueFinder(ref: ActorRef)

case class DestroyFinder(profileId: String)

case class ResolveChallenge(opponentId: String, accepted: Boolean)

case class ChallengeRequested(opponentId: String)

case class ApplyToFinder(opponentId: String, channel: ChannelContext)


object Finders extends ActorCreator {


  def props(bf: BattleField) = Props(classOf[Finders], bf.trench, bf.findTimeout, bf.caches)

}

case class Finders(trench: ActorRef, timeout: Timeout, cache: CacheStore) extends Actor with ActorLogging {

  private val pending = mutable.ArrayBuffer.empty[ActorRef]


  private val stashed = mutable.ArrayBuffer.empty[ActorRef]


  def newFinder(profileId: String) = {
    val profile = cache.profiles get profileId
    val finder = context.actorOf(Props(classOf[Finder], trench, profile, timeout))
    context.watch(finder)

    pending += finder
    finder
  }

  def receive = {

    case Find(profileId) => newFinder(profileId)


    case ApplyToFinder(opponentId: String, _) => {
      pending.headOption map {
        finder => {
          log.info("Found a pending users, removing from queue and resolving Finder")
          pending remove 0
          stashed += finder
          // update opponent state
          finder ! ChallengeRequested(opponentId)

        }
      } orElse {
        log.warning("No pending users waiting")
        None
      }
    }


    case Terminated(ref) => Seq(pending, stashed).map {
      collection =>
        val index = collection.indexOf(ref)
        if (index > -1) collection.remove(index)
    }
    case QueueFinder(ref) => if (!pending.contains(ref)) {
      pending += ref
    } else log info s"Finder already in queue $ref"
    case DestroyFinder(profileId) => {
      context.actorSelection(profileId) ! PoisonPill

    }
  }

}

case class FinderTimeout(creatorId: String) extends Exception(s"Wasn't able to find any opponents for ${creatorId}")

case class Finder(trench: ActorRef, profile: Profile, timeout: Timeout) extends Actor {

  import utils.Serialization.Writes._


  implicit val system = context.system
  lazy val selection = ActorSelection(self, "")
  val scheduler = system.scheduler

  val master = system.actorSelection("/user/master")
  private val alreadySeen = collection.mutable.ArrayBuffer.empty[String]

  def seen(profileId: String) = alreadySeen.contains(profileId)

  implicit val exec: ExecutionContext = system.dispatcher

  private val p = promise[War]

  private val INTERVAL = 5

  val creatorId = profile.id

  override def preStart() {
    super.preStart()
    trench ! FindOpponent(creatorId, alreadySeen, Some((selection, profile)))
  }


  override def postStop() {
    destroy
    super.postStop()
  }

  def destroy = {

    countdown.cancel()
    handle.cancel()

  }

  private val actorRef = Connection.actorFor(creatorId)


  var passed = 0
  private val countdown = scheduler.schedule(INTERVAL seconds, INTERVAL seconds) {
    passed += INTERVAL
    val msg: JsValue = Countdown(passed)
    actorRef ! msg


  }


  private val handle: Cancellable = scheduler.scheduleOnce(timeout.duration) {
    countdown.cancel()

    p.failure(FinderTimeout(creatorId))

  }

  def future = p.future

  future onSuccess {
    case _ => {
      destroy

    }
  }


  def receive = {


    case ChallengeRequested(opponentId) => trench ! RequestChallenge(selection, profile, opponentId)
    case ResolveChallenge(opponentId, accepted) => resolve(opponentId, accepted)
  }

  def resolve(opponentId: String, accepted: Boolean): Unit = if (accepted) {


    future onFailure {
      case x: Throwable => println(x)
    }
    p.completeWith(master.ask(NewWar(creatorId, opponentId))(Timeout(20 seconds)).mapTo[War])

  } else {
    alreadySeen += opponentId

    val profileId = profile.id

    trench ! FindOpponent(profileId, alreadySeen, Some((selection, profile)))



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
