package battle


import akka.actor._
import play.api.libs.json.JsValue
import akka.actor.Terminated
import models.{PointContext, War}
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/7/13
 * Time: 7:09 PM 
 */


object WarBattle {
  def apply(war: War, creatorId: String, opponentId: String, creator: ActorPath, opponent: ActorPath)(implicit system: ActorSystem) = {
    new WarBattle(war, creatorId, opponentId, system.actorSelection(creator), system.actorSelection(opponent))
  }
}

class WarBattle(war: War, creatorId: String, opponentId: String, creator: ActorSelection, opponent: ActorSelection) extends Actor with ActorLogging {


  import utils.Serialization.Writes.{pointsWrites, throwable2Json, appError2Json, wonWrites, warAcceptedWrites, event2JsValue}


  import models.CacheStore.{instance => caches}


  val channels = Seq(creator, opponent)

  val pointsNeededToWin = war.rules.points
  var creatorPoints = 0
  var opponentPoints = 0

  val activate = new AtomicBoolean()
  val ended = new AtomicBoolean();


  override def preStart() {
    super.preStart()
    creator ! Identify(creatorId)
    opponent ! Identify(opponentId)
    //  activeWars += (creatorId -> war.id)
    //activeWars += (opponentId -> war.id)

    log.info(s"Started War = ${war.id}")
  }


  def notifyDisconnect(profileId: String) = {
    log.info(s"Notify disconnect of profileId = $profileId")
    val profile = caches.profiles.get(profileId)
    val msg: JsValue = DisconnectError(profile)

    self ! PoisonPill
    channels foreach (_ ! msg)
    war ended


  }


  override def postStop() {
    super.postStop()

    Trench ! UnMarkPending(creatorId)
    Trench ! UnMarkPending(opponentId)
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
      case (winnerId, lose) => {
        ended.set(true)
        val msg: JsValue = Won(winnerId)
        channels foreach (_ ! msg)


        war won winnerId
        self ! PoisonPill
      }
    }

  }

  val ProfileId = "profile_([0-9]+)".r

  def ctxFor(profileId: String) = if (creatorId == profileId) creator else opponent

  def receive = {
    case ActorIdentity(id, Some(ref)) => {

      context.watch(ref)
      if (activate.get()) {
        context.become(active)
        log.info("Becomming active")
        val profiles = Seq(creatorId, opponentId)
        val msg: JsValue = profiles.map(caches.profiles get _) match {
          case Seq(c, o) => WarAccepted(c, o, war)
        }
        channels foreach (_ ! msg)
      }


      activate.set(true)


    }
    case ActorIdentity(id, None) => {
      log error s"Couldn't find ActorRef for profile = $id"
    }

  }

  def active: Actor.Receive = {
    case Terminated(r) => notifyDisconnect(r.path.name match {
      case ProfileId(profileId) => profileId
    })
    case a@WarAction(profileId, warId, action) if (!ended.get()) => {


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
    case x: Any => log.debug(s"Message not processed $x")
  }
}
