package models

import com.rethinkscala.net.Document
import Schema._
import com.fasterxml.jackson.annotation.{JsonCreator, JsonIgnore, JsonTypeInfo, JsonProperty}
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.rethinkscala.ast._
import org.joda.time.{Period, DateTime}
import play.api.libs.json.JsValue
import com.rethinkscala.Implicits._
import com.rethinkscala.ast.Var
import scala.Some
import com.rethinkscala.ast.Desc
import scala.util.{Try, Failure, Success}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 3:58 PM 
 */


case class RankFilter()


object Profile {

}

case class Profile(id: String, username: String, name: String, email: String, avatar: String) extends Document {

  @JsonIgnore
  lazy val firstName = name.split(" ").head
  @JsonIgnore
  lazy val lastName = name.split(" ").drop(1).mkString(" ")

  private def _rank(s: Sequence) = s.order(Desc("points")).indexesOf((v: Var) => (v \ "profileId").eq(id: Literal)).as[Int].right.toOption.map(_.headOption).flatten


  @JsonIgnore
  lazy val rank = _rank(Schema.stats)
  @JsonIgnore
  lazy val stats = Schema.stats.get(id).run.fold(_ => Stats(id), x => x)

  /*
  override protected def afterInsert {
    Schema.stats.insert(Stats(id)).run
  } */


  def rankBy(from: DateTime, to: DateTime) = _rank(Schema.stats.filter((v: Var) => ((v \ "createdAt") >= from.getMillis) and ((v \ "createdAt") <= to.getMillis)))

}


case class Point(id: Option[String] = None, profileId: String, warId: String, context: PointContext, createdAt: DateTime = DateTime.now()) extends Document {


  def contextAs[T](implicit mf: Manifest[T]) = if (mf.runtimeClass isAssignableFrom (context.getClass)) Some(context.asInstanceOf[T]) else None


}


case class Stats(@JsonProperty("id") profileId: String, wins: Int = 0, loses: Int = 0, points: Int = 0, battles: Int = 0) extends Document {
  def rank = (wins / (if (loses == 0) 1 else loses)) * points
}


object War {

  import scala.util.Random


  def create(creatorId: String, opponentId: String): Option[War] = {
    val names = Category.all.map {
      c => c.name: Datum
    }

    val q = (wars.filter {
      v => v \ "creatorId" === creatorId or v \ "opponentId" === creatorId or v \ "creatorId" === opponentId or v \ "opponentId" === opponentId
    } filter {
      v => v \ "createdAt" >= DateTime.now().minus(Period.hours(2)).getMillis
    }) \ "category" idiff (names: _*)

    val category = (q.as[String] match {
      case Left(e) => None
      case Right(e) => Random.shuffle(e).headOption.map(Category(_))
    }).flatten getOrElse Random.shuffle(Category.all).head



    War(None, creatorId, opponentId, Rules(category)) save match {
      case Left(e) => None
      case Right(r) => r.returnedValue[War]
    }
  }
}


case class Rules(category: Category, hashtag: Option[String] = Some("#pinterestwars"))

case class War(id: Option[String] = None, creatorId: String, opponentId: String, rules: Rules, createdAt: DateTime = DateTime.now()) extends Document {


  def category = rules.category

  def creator: Profile = ???

  def opponent: Profile = ???


}


trait WithProfile extends Document {
  val profileId: String

  def profile: Option[Profile] = profiles.get(profileId).asOpt
}


trait WithPoints extends WithProfile {
  val points: Int
}


@JsonTypeInfo(use = Id.CLASS, include = As.PROPERTY, property = "className")
abstract class PointContext extends WithPoints {
  val warId: String

  @JsonIgnore
  lazy val war = wars.get(warId).run match {
    case Left(e) => None
    case Right(w) => Some(w)
  }

  lazy val contextType = utils.StringHelper.lowerCaseWithUnderscore(getClass)

  def toJson: JsValue
}

case class Board(id: String, warId: String, profileId: String, name: String, category: Category, url: String, points: Int) extends PointContext {

  import utils.Serialization.Writes.boardWrites

  def toJson: JsValue = boardWrites writes this
}

case class Pin(id: String, warId: String, boardId: String, profileId: String, points: Int) extends PointContext {

  import utils.Serialization.Writes.pinWrites

  def board = ???

  def toJson = pinWrites writes this


}


case class Repin(id: String, warId: String, boardId: String, profileId: String, points: Int) extends PointContext {

  import utils.Serialization.Writes.repinWrites

  def toJson = repinWrites writes this
}

case class Comment(id: String, warId: String, pinId: String, profileId: String, points: Int) extends PointContext {

  import utils.Serialization.Writes.commentWrites

  def toJson = commentWrites writes this
}

case class Like(id: String, warId: String, profileId: String, points: Int) extends PointContext {

  import utils.Serialization.Writes.likeWrites

  def toJson = likeWrites writes this
}

case class Image(name: String, url: String, width: Int, height: Int)

case class Signup(id: Option[String] = None, profileId: String, activated: Boolean = false) extends Document

object Signup {
  def apply(profile: Profile): Signup = Signup(profileId = profile.id)
}



