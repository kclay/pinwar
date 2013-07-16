package controllers

import scala.concurrent.duration._
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.concurrent.Akka
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import battle._
import models.{Signup, Profile}
import play.api.data._
import play.api.data.Forms._

import play.api.Play.current
import utils.Mail

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 10:21 PM 
 */
object War extends Controller {


  import models.Schema._
  import play.api.libs.concurrent.Execution.Implicits._
  import scala.util.{Success, Failure}


  // Akka
  val battleField = Akka.system.actorOf(Props[BattleField], name = "battle_field")

  import battle.Extractor._

  lazy val findTimeout = Timeout(65, SECONDS)

  implicit def throwable2Json(e: Throwable) = Json.obj(
    "event" -> "error",
    "data" -> Json.obj(
      "message" -> e.getMessage
    )
  )

  case class Foo(a: String)

  val profileForm = Form(
    single(
      "profile" -> mapping(
        "id" -> text,
        "username" -> text,
        "name" -> text,
        "email" -> text,
        "avatar" -> text
      )(Profile.apply)(Profile.unapply)

    )
  )


  def signup = Action {
    implicit request =>
      profileForm.bindFromRequest fold(
        hasErrors => BadRequest("invalid"),
        profile => profiles.insert(profile).run match {
          case Left(e) => BadRequest
          case Right(r) => if (r.inserted == 1) {

            val rtn = (signups insert Signup(profile) withResults) run match {
              case Right(r) => r.returnedValue[Signup] map {
                s => {
                  Mail(profile.email, "Welcome to PinWar", views.html.email.signup(profile.name).body)
                  Created("")
                }

              }
              case Left(e) => Some(BadRequest)
            }

            rtn.get


          } else {
            BadRequest
          }

        }
        )


  }

  def index(profileId: String) = WebSocket.using[JsValue] {
    request =>
    // new client


      implicit val timeout = Timeout(5, SECONDS)

      val (out, channel) = Concurrent.broadcast[JsValue]

      val in = (battleField ? Connect(profileId, channel)).mapTo[ActorRef] map {
        broker =>

          Iteratee.foreach[JsValue] {
            _ match {
              case Find(f) => {
                val fu = (battleField.ask(f)(findTimeout)).mapTo[ActorRef]
                fu onComplete {
                  case Success(w) =>
                  case Failure(e) => channel.push(e)
                }
              }
              case Invite(r) =>

              //case a: Event.WarAction(_) =>


            }


          }
      }

      (Iteratee.flatten(in), out)
  }
}
