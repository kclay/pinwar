package utils

import battle.BattleField
import akka.actor._
import akka.util.Timeout
import akka.actor.Identify
import scala.concurrent.Future

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/15/13
 * Time: 2:42 PM 
 */
trait ActorCreator {

  import play.api.Mode._
  import akka.testkit.TestActorRef
  import akka.pattern.ask
  import scala.concurrent.duration._

  type ChildType

  lazy val actorName = utils.StringHelper.lowerCaseWithUnderscore(this getClass)

  def apply(bf: BattleField)(implicit system: ActorSystem): ActorRef = apply(bf, bf.currentMode)

  def apply(bf: BattleField, mode: Mode)(implicit system: ActorSystem): ActorRef = apply(props(bf), mode)

  def child(name: String)(implicit system: ActorSystem) = system.actorSelection(s"/user/$actorName/$name")

  def apply(p: Props, mode: Mode)(implicit system: ActorSystem): ActorRef = mode match {
    case Test => TestActorRef(p, actorName)
    case _ => system.actorOf(p, actorName)
  }

  def identify(name: String, timeout: Timeout = Timeout(10, SECONDS))(implicit system: ActorSystem): Future[ActorRef] = {
    implicit val ec = system.dispatcher
    child(name).ask(Identify(None))(timeout).mapTo[ActorIdentity].map(_.getRef)
  }

  def props(bf: BattleField): Props

}
