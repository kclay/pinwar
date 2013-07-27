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

  implicit val exec: ExecutionContext = ctx.system.dispatcher

  private val p = promise[War]

  private val INTERVAL = 5

  val creatorId = profile.id


  def destroy = {
    countdown.cancel()
    handle.cancel()
  }

  private val channel = ctx.trench.get(creatorId).get.channel
  var passed = 0
  private val countdown = ctx.system.scheduler.schedule(INTERVAL seconds, INTERVAL seconds) {
    passed += INTERVAL
    channel.push(Countdown(passed))


  }
  private val handle: Cancellable = ctx.system.scheduler.scheduleOnce(timeout.duration) {
    countdown.cancel()
    val index = ctx.pendingFinders.indexOf(this)
    if (index > -1)
      ctx.pendingFinders.remove(index)
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

      // TODO Send email
      c.channel.push(ChallengeRequest(ctx.challengeTokenFor(this), profile))
    }
  }

  def resolve(opponentId: String, accepted: Boolean) = {

    if (accepted) {

      future onSuccess {
        case war: War => {
          val profiles = Seq(creatorId, opponentId)
          val msg = profiles.map(ctx.caches.profiles get _) match {
            case Seq(c, o) => WarAccepted(c, o, war)
          }
          val ctxs = profiles.map(ctx.trench.get(_)).filter(_.isDefined)
          if (ctxs.size != 2) {
            // someone left
            ctx.pendingFinders += this

          } else {
            ctxs.flatten.foreach(_.channel.push(msg))
          }


        }
      }
      p.completeWith(ctx.master.ask(NewWar(creatorId, opponentId))(Timeout(20 seconds)).mapTo[War])

    } else {
      ctx.trench :=- opponentId // make opponent not in pending state
      ctx.pendingFinders += this // re add to queue
    }
  }


}
