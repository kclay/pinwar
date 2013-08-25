package models

import com.rethinkscala.Document
import akka.actor.{ActorSelection, ActorSystem}
import battle.ResolveChallenge
import models.Schema._

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/15/13
 * Time: 2:20 PM 
 */
case class ChallengeToken(id: Option[String] = None, finder: String) extends Document {


  def resolve(profileId: String, accepted: Boolean)(implicit system: ActorSystem) = system.actorSelection(finder) ! ResolveChallenge(profileId, accepted)


}

object ChallengeToken {


  def apply(id: String) = Schema[ChallengeToken].get(id).toOpt

  def apply(finder: ActorSelection): Option[ChallengeToken] = new ChallengeToken(finder = finder.toString()).save match {
    case Left(e) => None
    case Right(r) => r.returnedValue[ChallengeToken]
  }
}

