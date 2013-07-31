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
import play.api.cache.Cache
import Schema._
import com.rethinkscala.Implicits._

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/22/13
 * Time: 3:45 PM 
 */

abstract class CanTrack[T <: PointContext](implicit m: Manifest[T]) extends BattleAction {


  def categoryForBoard(boardId: String) = BattleAction.categoryForBoard(boardId)

  val action = m.runtimeClass.getSimpleName.toLowerCase


  def canTrackError(war: War): String

  def canTrack(war: War): Boolean

  def track(war: War, profileId: String): Either[Error, Points[T]] = {

    if (canTrack(war)) {

      val record = factory(war, profileId)


      //  val ct = this.asInstanceOf[CanTrack[T]]

      //implicit val mf = ct.mf
      val point = Point(profileId = profileId, warId = war.id, context = record)

      point.save match {
        case Right(b) => if (b.inserted == 1) {
          stats.get(profileId).update(s => (s \ "points") add record.points) run

          Right(Points[T](profileId, record.points, record))
        } else Left(new Error("unable to save"))
        case Left(e) => Left(new Error("Unable to save"))
      }
    } else {
      Left(new Error(canTrackError(war)))

    }

  }

  protected def factory(war: War, profileId: String): T


}

object BattleAction {

  import play.api.Play.current

  def categoryForBoard(boardId: String) = Cache.getOrElse[Category](s"boardCategory_$boardId", 5 * 60) {
    (boards.get(boardId) \ "category").asOpt[String] map (Category(_).getOrElse(Unknown)) getOrElse Unknown
  }
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

case class CreateBoard(id: String, name: String, description: String, category: Category, url: String) extends CanTrack[Board] {


  def read(value: JsValue) = value.asOpt[CreateBoard]

  def factory(war: War, profileId: String) = Board(id, war.id, profileId, name, category, url, points)

  private def descriptionOk(war: War) = war.rules.hashtag.map(description.contains(_)) getOrElse true

  private def categoryOk(war: War) = war.rules.category == category

  def canTrack(war: War) = categoryOk(war) && descriptionOk(war)

  def canTrackError(war: War) = {
    val b = new StringBuilder
    b append "Sorry! No points were awarded."
    val errorCategory = !categoryOk(war)
    val errorDescription = !descriptionOk(war)

    if (errorCategory)
      b append "You have to create items in the \"%s\" category".format(war.category)
    if (errorDescription) {
      if (errorCategory) b append " and "
      b append "You have to input \"%s\" in your board description".format(war.rules.hashtag)
    }

    b toString()

  }
}


trait PinAction {

  val id: String
  val board: Board

  val images: Seq[Image]

}

object CreateRepin {
  val action = "repin"

}

case class CreateRepin(id: String, boardId: String, category: Category, images: Seq[Image]) extends CanTrack[Repin] {


  def read(value: JsValue) = value.asOpt[CreateRepin]

  protected def factory(war: War, profileId: String) = Repin(id, war.id, boardId, profileId, points)

  def canTrack(w: War) = categoryForBoard(boardId) == category

  def canTrackError(war: War) = "Sorry! No points were awarded. You have to repin items in the \"%s\" category" format war.category
}

object CreatePin {
  val action = "pin"
}

case class CreatePin(id: String, boardId: String, images: Seq[Image]) extends CanTrack[Pin] {


  def read(value: JsValue) = value.asOpt[CreatePin]

  protected def factory(war: War, profileId: String) = Pin(id, war.id, boardId, profileId, points)

  def canTrack(war: War) = categoryForBoard(boardId) == war.category

  def canTrackError(war: War) = ???
}

object CreateComment {
  val action = "comment"
}

case class CreateComment(id: String, pinId: String, category: Category) extends CanTrack[Comment] {


  protected def factory(war: War, profileId: String) = Comment(id, war.id, pinId, profileId, points)


  def read(value: JsValue) = value.asOpt[CreateComment]

  def canTrackError(war: War) = "Sorry! No points were awarded. You have to comment on a item in the \"%s\" category" format war.category

  def canTrack(w: War) = w.category == category

}

object CreateLike {
  val action = "like"
}

case class CreateLike(id: String, category: Category) extends CanTrack[Like] {
  protected def factory(war: War, profileId: String) = Like(id, war.id, profileId, points)

  def read(value: JsValue) = value.asOpt[CreateLike]

  def canTrackError(war: War) = "Sorry! No points were awarded. You have to like a item in the \"%s\" category" format war.category

  def canTrack(w: War) = w.category == category
}

case class Confirm(profileId: String) extends BattleAction {
  type TrackType = WithPoints
  val action = "confirm"


  def read(value: JsValue) = value.asOpt[Confirm]

  protected def factory(war: War, profileId: String) = ???
}

case class Points[T](profileId: String, amount: Int, context: T)

case class Track(war: War)