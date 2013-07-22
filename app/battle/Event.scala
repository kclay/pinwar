package battle

import play.api.libs.json.{JsValue, Reads}
import utils.StringHelper._
import scala.Some
import play.api.libs.iteratee.Concurrent.Channel
import models.{War, Profile}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/22/13
 * Time: 3:46 PM 
 */

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

  case object HandleInvite extends CanBuild[HandleInvite]

}

trait Event {

  type Self <: Event

  val profileId: String


}


case class Invite(profileId: String, email: String) extends Event


case class NewWar(profileId: String, opponentId: String)

case class Disconnect(profileId: String)

case class Connect(profileId: String, channel: Channel[JsValue])

case class Find(profileId: String, timeout: Int = 60) extends Event

case class NewAccount(profile: Profile, token: Option[String] = None)

case class HandleInvite(profileId: String, token: String, accept: Boolean, profile: Profile) extends Event


case class WarAction(profileId: String, war: String, action: BattleAction) extends Event

case class WarAccepted(profileId: String, war: War) extends Event


