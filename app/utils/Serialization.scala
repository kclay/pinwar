package utils

import play.api.libs.json.{Writes => Write, Reads => Read}
import play.api.libs.json._
import utils.StringHelper._
import models._
import battle._
import models.Board
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


    implicit val createBoardReads = Json.reads[CreateBoard]

    implicit val createPinReads = Json.reads[CreatePin]

    implicit val createCommentReads = Json.reads[CreateComment]
    implicit val createLikeReads = Json.reads[CreateLike]


    implicit val rePinedReads = Json.reads[CreateRepin]
    implicit val confirmReads = Json.reads[Confirm]


    implicit val battleActionReads: Reads[BattleAction] = new Reads[BattleAction] {


      // have to supply hits so that intellij wont deadlock

      val all = Map(CreatePin.action -> createPinReads,
        CreateBoard.action -> createBoardReads,
        CreateComment.action -> createCommentReads,
        CreateRepin.action -> rePinedReads,
        CreateLike.action -> createLikeReads
      )


      def reads(json: JsValue) = {
        val action = (json \ "action").as[String]
        // TODO Fix Possible error
        all.get(action).get.reads(json).asInstanceOf[JsResult[BattleAction]]

      }
    }

    implicit val profileReads = Json.reads[Profile]
    implicit val warActionReads = Json.reads[WarAction]

    implicit val handleInviteReads = Json.reads[HandleInvite]
    implicit val findReads = Json.reads[Find]
    implicit val newGameReads = Json.reads[Invite]
    implicit val ruleReads = Json.reads[Rules]
    implicit val warReads = Json.reads[War]
    implicit val warAcceptedReads = Json.reads[WarAccepted]
    implicit val challengeRequestReads = Json.reads[ChallengeRequest]
    implicit val challengeResponseReads = Json.reads[ChallengeResponse]
    implicit val countdownReads = Json.reads[Countdown]


  }

  object Writes {

    implicit def appError2Json(e: AppError): JsValue = Json.obj(
      "event" -> "error",
      "data" -> Json.obj(
        "message" -> e.message,
        "kind" -> e.kind
      )
    )

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




    implicit val powerUpWrites = new OWrites[PowerUp] {
      def writes(o: PowerUp) = Json.obj(
        "name" -> lowerCaseWithUnderscore(o.getClass),
        "amount" -> o.amount,
        "data"-> o.data

      )
    }

    implicit val findWrites = Json.writes[Find]
    implicit val profileWrites = Json.writes[Profile]
    implicit val inviteWrites = Json.writes[Invite]
    implicit val rankingWrites = Json.writes[Stats]

    implicit val categoryWrites = new Writes[Category] {
      def writes(o: Category) = JsString(o.displayName)
    }

    implicit val ruleWrites = Json.writes[Rules]
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


    implicit val boardWrites = Json.writes[Board]
    implicit val repinWrites = Json.writes[Repin]

    implicit val commentWrites = Json.writes[Comment]

    implicit val likeWrites = Json.writes[Like]

    implicit val pinWrites = Json.writes[Pin]


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

    implicit def pointContext2JsValue[T <: PointContext](context: T)(implicit wsj: Writes[T]): JsValue = {
      var obj = wsj.writes(context).asInstanceOf[JsObject]
      obj +=("name", Json.toJson(lowerCaseWithUnderscore(context.getClass.getSimpleName)))
      obj
    }

    implicit val pointsWrites = new Writes[Points[PointContext]] {
      def writes(o: Points[PointContext]) = Json.obj(
        "event" -> "points",
        "data" -> Json.obj(
          "name" -> lowerCaseWithUnderscore(o.context.getClass),
          "amount" -> o.amount,
          "profileId" -> o.profileId,
          "context" -> o.context.toJson
        )
      )
    }


  }

}
