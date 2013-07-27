package models

import akka.actor.Actor
import java.util.UUID
import com.rethinkscala.net.Document
import Schema._
import com.fasterxml.jackson.annotation.{JsonIgnore, JsonTypeInfo, JsonProperty}
import battle.BattleAction
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.rethinkscala.ast.{Literal, Sequence, Desc, Var}
import org.joda.time.DateTime
import com.rethinkscala.Implicits._
import play.api.cache.Cache

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


  beforeInsert(() => afterInsert(() => Schema.stats.insert(Stats(id)).run))


  def rankBy(from: DateTime, to: DateTime) = _rank(Schema.stats.filter((v: Var) => ((v \ "createdAt") >= from.getMillis) and ((v \ "createdAt") <= to.getMillis)))

}


case class Point(id: Option[String] = None, profileId: String, warId: String, context: PointContext, createdAt: DateTime) extends Document {


  def contextAs[T](implicit mf: Manifest[T]) = if (mf.runtimeClass isAssignableFrom (context.getClass)) Some(context.asInstanceOf[T]) else None


}


case class Stats(@JsonProperty("id") profileId: String, wins: Int = 0, loses: Int = 0, points: Int = 0) extends Document {
  def rank = (wins / (if (loses == 0) 1 else loses)) * points
}


case class War(id: String, creatorId: String, opponentId: String) extends Document {


  def creator: Profile = ???

  def opponent: Profile = ???


}


trait WithProfile extends Document {
  val profileId: String

  def profile = ???
}


trait WithPoints extends WithProfile {
  val points: Int
}

@JsonTypeInfo(use = Id.CLASS, include = As.PROPERTY, property = "className")
abstract class PointContext extends WithPoints

case class Board(id: String, profileId: String, name: String, category: Category, url: String, points: Int) extends PointContext

case class Pin(id: String, boardId: String, profileId: String, points: Int) extends PointContext {
  def board = ???


}

case class Repin(id: String, boardId: String, profileId: String, points: Int) extends PointContext


case class Image(name: String, url: String, width: Int, height: Int)

case class Signup(id: Option[String] = None, profileId: String, activated: Boolean = false) extends Document

object Signup {
  def apply(profile: Profile): Signup = Signup(profileId = profile.id)
}



