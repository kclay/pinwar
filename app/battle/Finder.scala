package battle

import models.{War, Profile}
import akka.actor.{Cancellable, ActorRef}
import scala.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import play.api.libs.json.JsValue
import java.util.concurrent.atomic.AtomicReference


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/23/13
 * Time: 7:34 PM 
 */


sealed trait FinderState

case object InFlight extends FinderState

case object Waiting extends FinderState

case class FinderTimeout(creatorId: String) extends Exception(s"Wasn't able to find any opponents for ${creatorId}")

case class Finder(ctx: BattleField, profile: Profile, sender: ActorRef, timeout: Timeout) {

  import utils.Serialization.Writes._

  implicit val system = ctx.system

  private val alreadySeen = collection.mutable.ArrayBuffer.empty[String]

  def seen(profileId: String) = alreadySeen.contains(profileId)

  implicit val exec: ExecutionContext = ctx.system.dispatcher

  private val p = promise[War]

  private val INTERVAL = 5

  val creatorId = profile.id

  private[this] val finderState = new AtomicReference[FinderState](Waiting)


  def state: FinderState = finderState.get()

  def destroy = {
    ctx.pendingFinders -= this
    ctx.finders -= this
    countdown.cancel()
    handle.cancel()
    countdown.cancel()

  }

  private val channel = ctx.trench.get(creatorId).get
  var passed = 0
  private val countdown = ctx.system.scheduler.schedule(INTERVAL seconds, INTERVAL seconds) {
    passed += INTERVAL
    val msg: JsValue = Countdown(passed)
    channel ! msg


  }


  private val handle: Cancellable = ctx.system.scheduler.scheduleOnce(timeout.duration) {
    destroy
    val msg: JsValue = new Error("Unable to find any opponents to challenge.")
    channel ! msg
    p.failure(FinderTimeout(creatorId))

  }

  def future = p.future

  future onSuccess {
    case _ => {
      destroy

    }
  }


  def request(opponentId: String) = ctx.trench.get(opponentId) map {
    c => {

      ctx.trench :=+ opponentId
      // TODO Send email
      finderState.set(InFlight)
      c ! (ChallengeRequest(ctx.challengeTokenFor(this), profile): JsValue)

    }
  }

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
      ctx.trench.find {
        case (p, c) => p != profileId && c.available && !alreadySeen.contains(p)
      } match {
        case Some((opponentId, _)) => {

          request(opponentId)
        }
        case _ => {
          ctx.pendingFinders += this
          finderState.set(Waiting)
        } // re add to queue
      }
      ctx.trench block opponentId // make opponent not in pending state


    }
  }


}
