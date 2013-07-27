package utils

import play.api.libs.json._
import utils.StringHelper._
import models._
import play.api.libs.json.JsSuccess
import scala.Some
import battle._
import play.api.libs.json.JsSuccess
import scala.Some
import play.api.libs.json.JsSuccess
import scala.Some
import play.api.libs.json.JsSuccess
import scala.Some
import battle.Repined
import battle.WarAction
import battle.Invite
import battle.CreatePin
import play.api.libs.json.JsSuccess
import battle.Find
import scala.Some
import battle.CreateBoard
import models.Board
import battle.Repined
import battle.WarAction
import battle.Invite
import battle.CreatePin
import play.api.libs.json.JsSuccess
import battle.Find
import scala.Some
import models.Image
import battle.CreateBoard

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/16/13
 * Time: 9:20 AM 
 */
object Serialization {

  object Reads {


    implicit val categoryRead = new Reads[Category] {
      def reads(json: JsValue) = json.asOpt[String] match {
        case Some(name) => Category.all.filter(_.name == name).headOption.map(JsSuccess[Category](_)).getOrElse(JsError())
        case _ => JsError()
      }
    }


    implicit val boardRead = Json.reads[Board]


    implicit val imagesReads = Json.reads[Image]


    implicit val createBoard = Json.reads[CreateBoard]

    implicit val createPin = Json.reads[CreatePin]


    implicit val rePined = Json.reads[Repined]
    implicit val confirmReads = Json.reads[Confirm]


    implicit val battleActionReads: Reads[BattleAction] = new Reads[BattleAction] {


      // have to supply hits so that intellij wont deadlock

      val all = Map("create_pin" -> createPin,
        "create_board" -> createBoard,
        "re_pined" -> rePined,
        "confirm" -> confirmReads
      )


      def reads(json: JsValue) = all.get((json \ "action").as[String]).map(_.reads(json)).getOrElse(JsError())
    }
    implicit val profileReads = Json.reads[Profile]
    implicit val warActionReads = Json.reads[WarAction]

    implicit val handleInviteReads = Json.reads[HandleInvite]
    implicit val findReads = Json.reads[Find]
    implicit val newGameReads = Json.reads[Invite]
    implicit val warReads = Json.reads[War]
    implicit val warAcceptedReads = Json.reads[WarAccepted]
    implicit val challengeRequestReads = Json.reads[ChallengeRequest]
    implicit val challengeResponseReads = Json.reads[ChallengeResponse]
    implicit val countdownReads = Json.reads[Countdown]


  }

  object Writes {

    implicit def throwable2Json(e: Throwable): JsValue = withError(e.getMessage)

    def withError(message: String) = Json.obj(
      "event" -> "error",
      "data" -> Json.obj(
        "message" -> message
      )
    )

    def withFeedback(message: String) = Json.obj(
      "event" -> "feedback",
      "data" -> Json.obj(
        "message" -> message
      )
    )

    implicit val findWrites = Json.writes[Find]
    implicit val profileWrites = Json.writes[Profile]
    implicit val inviteWrites = Json.writes[Invite]
    implicit val rankingWrites = Json.writes[Stats]

    implicit val warWrites = Json.writes[War]


    implicit val warAcceptedWrites = new OWrites[WarAccepted] {
      def writes(o: WarAccepted) = Json.obj("war" -> Json.toJson(o.war),
        "creator" -> withStats(o.creator),
        "opponent" -> withStats(o.opponent)
      )
    }
    implicit val countdownWrites = Json.writes[Countdown]


    def withStats(o: Profile): JsObject = {
      var profile: JsObject = profileWrites.writes(o).asInstanceOf[JsObject]
      profile +=("rank", Json.toJson(o.rank))
      profile +=("stats", Json.toJson(o.stats))
      profile
    }

    implicit val challengeRequestWrites = new OWrites[ChallengeRequest] {
      def writes(o: ChallengeRequest) = {

        Json.obj(
          "token" -> o.token,
          "profile" -> withStats(o.profile)
        )
      }
    }


    implicit val challengeResponseWrites = Json.writes[ChallengeResponse]

    implicit def event2JsValue[T <: Event](event: T)(implicit wsj: Writes[T]): JsValue = {
      apply(event)
    }

    implicit def event2String[T <: Event](event: T)(implicit wsj: Writes[T]): String = {
      apply(event).toString()
    }

    def apply[T <: Event](event: T)(implicit wjs: Writes[T]) = {
      Json.obj(
        "event" -> lowerCaseWithUnderscore(event.getClass.getSimpleName),
        "data" -> wjs.writes(event)
      )
    }


  }

}
