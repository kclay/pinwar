package battle

import models._
import play.api.libs.json.JsValue
import models.Pin
import models.Board
import models.War
import models.Repin
import models.Image
import utils.Serialization.Reads._

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/22/13
 * Time: 3:45 PM 
 */

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
      case Right(b) => if (b.inserted == 1) Right(Points[TrackType](profileId, record.points, record)) else Left(new Error("unable to save"))
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

case class Confirm(profileId: String) extends BattleAction {
  type TrackType = WithPoints
  val action = "confirm"


  def read(value: JsValue) = value.asOpt[Confirm]

  protected def factory(war: War, profileId: String) = ???
}

case class Points[T](profileId: String, amount: Int, context: T)

case class Track(war: War)