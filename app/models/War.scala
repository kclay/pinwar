package models

import akka.actor.Actor
import java.util.UUID
import com.rethinkscala.net.Document

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 3:58 PM 
 */


case class Profile(id: String, username: String, name: String, email: String, icon: String) extends Document

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


case class Board(id: String, profileId: String, name: String, category: Category, url: String, points: Int) extends WithPoints

case class Pin(id: String, boardId: String, profileId: String, points: Int) extends WithPoints {
  def board = ???


}

case class Repin(id: String, boardId: String, profileId: String, points: Int) extends WithPoints


case class Image(name: String, url: String, width: Int, height: Int)

case class Signup(id: Option[String] = None, profileId: String, activated: Boolean = false) extends Document

object Signup {
  def apply(profile: Profile):Signup = Signup(profileId = profile.id)
}



