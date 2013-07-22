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


      def reads(json: JsValue) = all.get((json \ "name").as[String]).map(_.reads(json)).getOrElse(JsError())
    }
    implicit val profileReads = Json.reads[Profile]
    implicit val warActionReads = Json.reads[WarAction]

    implicit val handleInviteReads = Json.reads[HandleInvite]
    implicit val findReads = Json.reads[Find]
    implicit val newGameReads = Json.reads[Invite]


  }

  object Writes {


    implicit val findWrites = Json.writes[Find]
    implicit val profileWrites = Json.writes[Profile]
    implicit val inviteWrites = Json.writes[Invite]
    implicit val rankingWrites = Json.writes[Stats]

    implicit val warWrites = Json.writes[War]

    implicit val warAcceptedWrites = Json.writes[WarAccepted]
    /* implicit val eventWrite = new Writes[Event] {


       def writes(e: Event) = {
         implicit val wjs = this


         Json.obj(
           "event" -> lowerCaseWithUnderscore(e.getClass.getSimpleName),
           "data" -> Json.toJson(e)
         )
       }
     } */
  }

}
