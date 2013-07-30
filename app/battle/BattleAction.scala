package battle

import models._
import play.api.libs.json.JsValue
import models.Pin
import models.Board
import models.War
import models.Repin
import models.Image
import utils.Serialization.Reads._
import org.joda.time.DateTime

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/22/13
 * Time: 3:45 PM 
 */

abstract class CanTrack[T <: PointContext](implicit m: Manifest[T]) extends BattleAction {

  import Schema._

  val action = m.runtimeClass.getSimpleName.toLowerCase

  //type TrackType = T

  // lazy val mf: Manifest[TrackType] = m

  def track(war: War, profileId: String): Either[Error, Points[T]] = {
    val record = factory(war, profileId)


    //  val ct = this.asInstanceOf[CanTrack[T]]

    //implicit val mf = ct.mf
    val point = Point(profileId = profileId, warId = war.id, context = record)

    point.save match {
      case Right(b) => if (b.inserted == 1) Right(Points[T](profileId, record.points, record)) else Left(new Error("unable to save"))
      case Left(e) => Left(new Error("Unable to save"))
    }
  }

  protected def factory(war: War, profileId: String): T


}

sealed trait BattleAction {


  val action: String
  type Self = this.type

  lazy val points = play.api.Play.configuration(play.api.Play.current).getInt(s"points.${action}").get


  def read(value: JsValue): Option[BattleAction]


  def unapply(value: JsValue) = (value \ "name").asOpt[String] map {
    case n if (n.equals(action)) => read(value)
    case _ => None
  }


}


object CreateBoard {
  val action = "board"
}

case class CreateBoard(id: String, name: String, category: Category, url: String) extends CanTrack[Board] {


  def read(value: JsValue) = value.asOpt[CreateBoard]

  def factory(war: War, profileId: String) = Board(id, war.id, profileId, name, category, url, points)
}


trait PinAction {

  val id: String
  val board: Board

  val images: Seq[Image]

}

object CreateRepin {
  val action = "repin"

}

case class CreateRepin(id: String, board: Board, images: Seq[Image]) extends CanTrack[Repin] {


  def read(value: JsValue) = value.asOpt[CreateRepin]

  protected def factory(war: War, profileId: String) = Repin(id, war.id, board.id, profileId, points)
}

object CreatePin {
  val action = "pin"
}

case class CreatePin(id: String, board: Board, images: Seq[Image]) extends CanTrack[Pin] {


  def read(value: JsValue) = value.asOpt[CreatePin]

  protected def factory(war: War, profileId: String) = Pin(id, war.id, board.id, profileId, points)
}

object CreateComment {
  val action = "comment"
}

case class CreateComment(id: String, pinId: String) extends CanTrack[Comment] {


  protected def factory(war: War, profileId: String) = Comment(id, war.id, pinId, profileId, points)


  def read(value: JsValue) = value.asOpt[CreateComment]
}

object CreateLike {
  val action = "like"
}

case class CreateLike(id: String) extends CanTrack[Like] {
  protected def factory(war: War, profileId: String) = Like(id, war.id, profileId, points)

  def read(value: JsValue) = value.asOpt[CreateLike]
}

case class Confirm(profileId: String) extends BattleAction {
  type TrackType = WithPoints
  val action = "confirm"


  def read(value: JsValue) = value.asOpt[Confirm]

  protected def factory(war: War, profileId: String) = ???
}

case class Points[T](profileId: String, amount: Int, context: T)

case class Track(war: War)