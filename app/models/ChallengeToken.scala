package models

import com.rethinkscala.Document
import akka.actor.{ActorSelection, ActorSystem}
import battle.{Finders, ResolveChallenge}
import models.Schema._

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/15/13
 * Time: 2:20 PM 
 */
case class ChallengeToken(id: Option[String] = None, selection: String, profileId: String) extends Document {


  def resolve(opponentId: String, accepted: Boolean)(implicit system: ActorSystem) = system.actorSelection(selection) ! ResolveChallenge(profileId, opponentId, accepted)


}

object ChallengeToken {


  def apply(id: String) = Schema[ChallengeToken].get(id).toOpt

  def apply(profileId: String, finder: ActorSelection): Option[ChallengeToken] = new ChallengeToken(selection = finder, profileId = profileId).save match {
    case Left(e) => None
    case Right(r) => r.returnedValue[ChallengeToken]
  }
}

