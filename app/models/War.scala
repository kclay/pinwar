package models

import com.rethinkscala.net.Document
import Schema._
import com.fasterxml.jackson.annotation._
import com.rethinkscala.ast._
import org.joda.time.{Period, DateTime}
import play.api.libs.json.{Json, JsObject, JsValue}
import com.rethinkscala.Implicits._
import play.api.Logger
import scala.collection.mutable.ArrayBuffer
import com.rethinkscala.ast.Var
import scala.Some
import com.rethinkscala.ast.Desc

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

  private def _rank(s: Sequence) = s.order("points" desc).indexesOf((v: Var) => (v \ "profileId").eq(id: Literal)).as[Int].right.toOption.map {
    x => {
      Logger.info(s"Rank : $x")
      x.headOption
    }
  }.flatten


  @JsonIgnore
  lazy val rank = _rank(Schema.stats)
  @JsonIgnore
  lazy val stats = Schema.stats.get(id).run.fold(_ => Stats(id), x => x)


  override protected def afterInsert {
    Schema.stats.insert(Stats(id)).run
  }


  def rankBy(from: DateTime, to: DateTime) = _rank(Schema.stats.filter((v: Var) => ((v \ "createdAt") >= from.getMillis) and ((v \ "createdAt") <= to.getMillis)))

}


case class Point(id: Option[String] = None, profileId: String, warId: String, context: PointContext, createdAt: DateTime = DateTime.now()) extends Document {


  def contextAs[T](implicit mf: Manifest[T]) = if (mf.runtimeClass isAssignableFrom (context.getClass)) Some(context.asInstanceOf[T]) else None


  override protected def afterInsert(id: String) {
    stats.get(profileId).update(s => (s \ "points") add context.points) run
  }

}


case class Stats(@JsonProperty("id") profileId: String, wins: Int = 0, loses: Int = 0, points: Int = 0, battles: Int = 0) extends Document {
  def rank = (wins / (if (loses == 0) 1 else loses)) * points
}


object War {

  import scala.util.Random


  def create(creatorId: String, opponentId: String): Option[War] = {
    val names = Category.forBattle map {
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
    }).flatten getOrElse Random.shuffle(Category.forBattle).head



    War(None, creatorId, opponentId, Rules(category)) save match {
      case Left(e) => None
      case Right(r) => r.returnedValue[War]
    }
  }
}


case class Rules(category: Category, points: Int = 10000, hashtag: Option[String] = Some("#pinterestwars"))

case class War(id: Option[String] = None, creatorId: String, opponentId: String, rules: Rules, createdAt: DateTime = DateTime.now(),
               endedAt: Option[DateTime] = None, winnerId: Option[String] = None) extends Document {


  // TODO : Cache


  def boardFor(profileId: String) = Fetch.boardByProfile(id.get, profileId)

  def boardCategory(profileId: String) = Fetch.boardCategory(id.get, profileId)

  def category = rules.category

  def hashtag = rules.hashtag

  def creator: Profile = ???

  def opponent: Profile = ???

  def ended = copy(endedAt = Some(DateTime.now())).save


  override protected def afterInsert(id: String) {
    stats.get(creatorId).update(s => s \ "battles" add 1) run

    stats.get(opponentId).update(s => s \ "battles" add 1) run
  }

  def won(id: String) = {
    copy(endedAt = Some(DateTime.now()), winnerId = Some(id)) save

    stats.get(id).update(s => s \ "wins" add 1) run

    stats.get(if (id == creatorId) opponentId else creatorId).update(s => s \ "loses" add 1) run
  }


}


trait WithProfile extends Document {
  val profileId: String

  def profile: Option[Profile] = Fetch.profile(profileId)
}


abstract class WithPoints extends WithProfile {


  var points: Int


}


abstract class PointContext extends WithPoints {
  val warId: String

  @JsonIgnore
  lazy val war = wars.get(warId).run match {
    case Left(e) => None
    case Right(w) => Some(w)
  }

  lazy val contextType = utils.StringHelper.lowerCaseWithUnderscore(getClass)

  def toJson: JsValue = asJson

  protected def asJson: JsValue

}


object PowerUp {
  lazy val all = Seq(Description)


}

trait PowerUp {
  val amount: Int

  def data: String

  lazy val name = utils.StringHelper.lowerCaseWithUnderscore(getClass)


}

case class Description(tag: String) extends PowerUp {
  val amount = 100

  def data = tag
}

trait PowerUpAble {
  self: PointContext =>

  import utils.Serialization.Writes.powerUpWrites

  @JsonIgnore
  val powerUps = ArrayBuffer.empty[PowerUp]

  def addPowerUp(up: PowerUp) = {
    powerUps.append(up)
    points = points + up.amount
  }

  override def toJson = asJson.asInstanceOf[JsObject].+(("powerUps", Json.toJson(powerUps)))


}

case class Board(id: String, warId: String, profileId: String, name: String, category: Category, url: String, var points: Int) extends PointContext {

  import utils.Serialization.Writes.boardWrites

  protected def asJson: JsValue = boardWrites writes this
}

case class Pin(id: String, warId: String, boardId: String, profileId: String, var points: Int) extends PointContext with PowerUpAble {

  import utils.Serialization.Writes.pinWrites

  def board = ???

  protected def asJson = pinWrites writes this


}


case class Repin(id: String, warId: String, boardId: String, profileId: String, var points: Int) extends PointContext with PowerUpAble {

  import utils.Serialization.Writes.repinWrites

  protected def asJson = repinWrites writes this
}

case class Comment(id: String, warId: String, pinId: String, profileId: String, var points: Int) extends PointContext with PowerUpAble {

  import utils.Serialization.Writes.commentWrites

  protected def asJson = commentWrites writes this
}

case class Like(id: String, warId: String, profileId: String, var points: Int) extends PointContext {

  import utils.Serialization.Writes.likeWrites

  protected def asJson = likeWrites writes this
}

case class Image(name: String, url: String, width: Int, height: Int)

case class Signup(id: Option[String] = None, profileId: String, activated: Boolean = false) extends Document

object Signup {
  def apply(profile: Profile): Signup = Signup(profileId = profile.id)
}



