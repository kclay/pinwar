package battle

import models._
import play.api.libs.json.JsValue
import utils.Serialization.Reads._
import Schema._
import com.rethinkscala.Implicits._
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import models.Pin
import models.Point
import models.Repin
import models.Image
import models.Like
import models.Board
import models.Comment

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/22/13
 * Time: 3:45 PM 
 */


trait TrackContext {
  self: CanTrack[_ <: PointContext] =>
  type ContextType <: PointContext

  def contextClass = pointClass


}


abstract class CanTrack[T <: PointContext](implicit m: Manifest[T]) extends BattleAction with TrackContext {


  type ContextType = T


  def pointClass = m.runtimeClass


  val action = m.runtimeClass.getSimpleName.toLowerCase


  def canTrackError(war: War, profileId: String): String

  def canTrack(war: War, profileId: String): Boolean

  def track(war: War, profileId: String): Either[Error, Points[T]] = {

    if (canTrack(war, profileId)) {


      val record = factory(war, profileId)


      if (isInstanceOf[CanPowerUp]) asInstanceOf[CanPowerUp].check(war, record)

      record.save fold(x => Left(new Error("Unable to save points, no points awarded")), r => {

        //val record = r.returnedValue[T].get
        val point = Point(profileId = profileId, warId = war.id, context = record)

        point.save match {
          case Right(b) => if (b.inserted == 1) {


            Right(Points[T](profileId, record.points, record))
          } else Left(new Error("unable to save"))
          case Left(e) => Left(new Error("Unable to save"))
        }

      })
      //implicit val mf = ct.mf


    } else {
      Left(new Error(canTrackError(war, profileId)))
    }
  }


  protected def factory(war: War, profileId: String): T


}


sealed trait BattleAction {


  val action: String


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


  def table = boards

  def read(value: JsValue) = value.asOpt[CreateBoard]

  def factory(war: War, profileId: String) = Board(id, war.id, profileId, name, category, url, points)

  private def descriptionOk(war: War) = war.rules.hashtag.map(description.contains(_)) getOrElse true

  private def categoryOk(war: War) = war.rules.category == category

  def canTrack(war: War, profileId: String) = war.boardFor(profileId).isEmpty && categoryOk(war) && descriptionOk(war)

  def canTrackError(war: War, profileId: String) = {
    val b = new StringBuilder
    b append "Sorry! No points were awarded."
    val errorCategory = !categoryOk(war)
    val errorDescription = !descriptionOk(war)
    val errorBoard = war.boardFor(profileId).isDefined
    if (errorBoard) {
      b append "You already created a board for this battle."
    } else {
      if (errorCategory)
        b append "The board must be in the \"%s\" category".format(war.category)
      if (errorDescription) {
        if (errorCategory) b append " and "
        b append "You must have the hashtag \"%s\" in your board description".format(war.rules.hashtag.get)
      }
      b append ".Delete board and try again"
    }

    b toString()

  }
}


trait PinAction {

  val id: String
  val board: Board

  val images: Seq[Image]

}

trait CanPowerUp {
  self: TrackContext {type ContextType <: PowerUpAble} =>


  def check(war: War, context: AnyRef) = {

    if (contextClass.isAssignableFrom(context.getClass)) {
      checkForPowerUp(war, context.asInstanceOf[ContextType])
    }


  }

  protected def checkForPowerUp(war: War, context: ContextType) = {}
}

object CreateRepin {
  val action = "repin"

}

trait DescriptionPowerUp extends CanPowerUp {
  self: TrackContext {type ContextType <: PowerUpAble} =>
  def descriptionForPowerUp: String

  override protected def checkForPowerUp(war: War, context: ContextType) = {
    super.checkForPowerUp(war, context)
    war.hashtag.map {
      ht => if (descriptionForPowerUp.contains(ht)) context.addPowerUp(Description(ht))
    }
  }
}

// TODO drop
/**
 *
 * @param id        New pin id
 * @param boardId   Board id were the pin was repinned to
 * @param category  Category in which the pin was in before the repin
 * @param description Description sent
 * @param images
 */
case class CreateRepin(id: String, boardId: String, category: Category, description: String, images: Seq[Image]) extends CanTrack[Repin] with DescriptionPowerUp {


  def read(value: JsValue) = value.asOpt[CreateRepin]

  protected def factory(war: War, profileId: String) = Repin(id, war.id, boardId, profileId, points)

  def canTrack(war: War, profileId: String) = war.boardFor(profileId).map {
    b => b.id == boardId && b.category == category
  } getOrElse false

  def canTrackError(war: War, profileId: String) = (war.boardFor(profileId) map {
    b => category match {
      case NoCategory => "This Re-Pin has NO CATEGORY. Please re-pin items in the \"%s\" category"
      case _ => "Sorry! No points were awarded. You have to re-pin items in the \"%s\" category"

    }
  } getOrElse "Sorry! No points were awarded. You have to create a board in \"%s\" category first") format war.category.displayName

  def descriptionForPowerUp = description
}

object CreatePin {
  val action = "pin"
}

/**
 *
 * @param id   New pin Id
 * @param boardId Board id were the pin was added to
 * @param description  Description of the pin
 * @param images
 */
case class CreatePin(id: String, boardId: String, description: String, images: Seq[Image]) extends CanTrack[Pin] with DescriptionPowerUp {


  def read(value: JsValue) = value.asOpt[CreatePin]

  protected def factory(war: War, profileId: String) = Pin(id, war.id, boardId, profileId, points)


  def canTrack(war: War, profileId: String) = war.boardFor(profileId) map (_.id == boardId) getOrElse false


  def canTrackError(war: War, profileId: String) = war.boardFor(profileId) map {
    b => "Sorry! No points were awarded. You have to pin items to \"%s\" board" format b.name
  } getOrElse "Sorry! No points were awarded. You have to create a board in \"%s\" category first" format war.category.displayName

  def descriptionForPowerUp = description
}

object CreateComment {
  val action = "comment"
}

case class CreateComment(id: String, pinId: String, content: String, category: Category) extends CanTrack[Comment] with DescriptionPowerUp {


  protected def factory(war: War, profileId: String) = Comment(id, war.id, pinId, profileId, points)


  def read(value: JsValue) = value.asOpt[CreateComment]

  def canTrackError(war: War, profileId: String) = (category match {
    case NoCategory => "This Pin has NO CATEGORY. Please comment on items in the \"%s\" category"
    case _ => "Sorry! No points were awarded. You have to comment on a item in the \"%s\" category"
  }) format war.category.displayName

  def canTrack(w: War, profileId: String) = w.category == category

  def descriptionForPowerUp = content
}

object CreateLike {
  val action = "like"
}

case class CreateLike(id: String, category: Category) extends CanTrack[Like] {
  protected def factory(war: War, profileId: String) = Like(id, war.id, profileId, points)

  def read(value: JsValue) = value.asOpt[CreateLike]

  def canTrackError(war: War, profileId: String) = (category match {
    case NoCategory => "This Like has NO CATEGORY. Please like items in the \"%s\" category"
    case _ => "Sorry! No points were awarded. You have to like a item in the \"%s\" category"
  }) format war.category.displayName

  def canTrack(w: War, profileId: String) = w.category == category
}

case class Confirm(profileId: String) extends BattleAction {
  type TrackType = WithPoints
  val action = "confirm"


  def read(value: JsValue) = value.asOpt[Confirm]

  protected def factory(war: War, profileId: String) = ???
}

case class Points[T <: PointContext](profileId: String, amount: Int, @JsonTypeInfo(use = Id.CLASS, include = As.PROPERTY, property = "className") context: T)

case class Track(war: War)