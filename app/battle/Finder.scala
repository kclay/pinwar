package battle

import models.{War, Profile}
import akka.actor.{Cancellable, ActorRef}
import scala.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import play.api.libs.json.JsValue


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/23/13
 * Time: 7:34 PM 
 */


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


  def destroy = {

    countdown.cancel()
    handle.cancel()
  }

  private val channel = ctx.trench.get(creatorId).get
  var passed = 0
  private val countdown = ctx.system.scheduler.schedule(INTERVAL seconds, INTERVAL seconds) {
    passed += INTERVAL
    channel ! Countdown(passed)


  }
  private val handle: Cancellable = ctx.system.scheduler.scheduleOnce(timeout.duration) {
    countdown.cancel()
    var index = ctx.pendingFinders.indexOf(this)
    if (index > -1)
      ctx.pendingFinders.remove(index)
    index = ctx.finders.indexOf(this)
    if (index > -1)
      ctx.finders.remove(index)
    p.failure(FinderTimeout(creatorId))

  }

  def future = p.future

  future onSuccess {
    case _ => {
      countdown.cancel()
      handle.cancel()
    }
  }


  def request(opponentId: String) = ctx.trench.get(opponentId) map {
    c => {

      ctx.trench :=+ opponentId
      // TODO Send email
      c ! (ChallengeRequest(ctx.challengeTokenFor(this), profile): JsValue)

    }
  }

  def resolve(opponentId: String, accepted: Boolean) = {

    if (accepted) {

      future onSuccess {
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
      }
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


}
