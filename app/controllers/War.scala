package controllers

import scala.concurrent.duration._
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import battle._
import models.{Fetch, Signup, Profile}
import play.api.data._
import play.api.data.Forms._

import play.api.Play.current
import utils.Mail
import actions.WithCors
import play.api.cache.Cache


import scala.Some
import com.rethinkscala.net.RethinkNoResultsError
import play.api.libs.json.JsObject


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 10:21 PM 
 */
object War extends Controller with WithCors {


  import models.Schema._
  import play.api.libs.concurrent.Execution.Implicits._
  import scala.util.{Success, Failure}
  import utils.Serialization.Writes._


  // Akka
  lazy val scope = BattleField

  val log = play.api.Logger

  lazy val ctx = scope.instance
  lazy val master = ctx.master
  lazy val caches = scope.caches


  case class Foo(a: String)

  val profileForm = Form(
    tuple(

      "profile" -> mapping(
        "id" -> text,
        "username" -> text,
        "name" -> text,
        "email" -> text,
        "avatar" -> text
      )(Profile.apply)(Profile.unapply),
      "token" -> optional(text)

    )
  )


  def player(p: Profile) = AllowCors {
    implicit request =>
      var jsp = profileWrites.writes(p).asInstanceOf[JsObject]
      jsp = jsp +("rank", Json.toJson(p.rank))
      jsp = jsp +("stats", Json.toJson(p.stats))
      Ok(jsp)
  }

  def confirm(token: String) = AllowCors {
    implicit request =>

      signups.get(token) run match {
        case Left(e) => BadRequest("Invalid Token")
        case Right(s) => {

          s.copy(activated = true).replace
          Ok("Thanks for confirming your account, get ready to battle!")
        }

        //master! NewAccount()

      }

  }


  def sendSignupEmail(s: Signup, profile: Profile)(implicit r: RequestHeader) = {
    Mail(profile.email, "Welcome to PinWar", views.html.email.welcome(profile.name, s.id.get).body)
    Created("Your email has been sent, please click on the link in it to continue the signup process")
  }

  def sendInviteEmail(from: Profile, email: String, token: String)(implicit r: RequestHeader) = {
    Mail(email, "You have been challenged", views.html.email.challenge(from, token).body)
  }

  def newSignup(profile: Profile, token: Option[String] = None)(implicit rh: RequestHeader): SimpleResult = {

    val p = token.map {
      t => {
        // TODO ensure data is removed
        val email = Cache.getAs[String](t)

        email.map(e => profile.copy(email = e))
      }
    }.flatten.getOrElse(profile)

    profiles.insert(p).run match {
      case Left(e) => BadRequest("Already registered")
      case Right(r) => if (r.inserted == 1) {


        ((signups insert Signup(profileId = profile.id, activated = token.isDefined) withResults) run match {
          case Right(r) => if (token.isEmpty) r.returnedValue[Signup] map (sendSignupEmail(_, profile))
          else Some(Ok(""))


          case Left(e) => Some(BadRequest)
        }).get


      } else {
        BadRequest
      }

    }
  }

  def signup = AllowCors {
    implicit request =>
      profileForm.bindFromRequest fold(
        hasErrors => BadRequest("invalid"), {
        case (profile, token) =>
          (profiles.get(profile.id) run match {
            case Left(e: RethinkNoResultsError) => newSignup(profile, token)
            case Left(e) => BadRequest("Unknown Error")
            case Right(r) => (signups filter Map("profileId" -> profile.id)).as[Signup] match {

              case Left(e) => BadRequest("")
              case Right(s) => sendSignupEmail(s.head, profile)


            }
          })


      }

        )


  }


  def index(profileId: String, fromInvite: Boolean) = WebSocket.using[JsValue] {
    implicit request =>
    // new client


      implicit val timeout = Timeout(20, SECONDS)

      val (out, channel) = Concurrent.broadcast[JsValue]



      var profile = Fetch.profile(profileId)


      master ! Connect(profileId, channel, fromInvite)
      val in = Iteratee.foreach[JsValue] {
        js =>
          log info (js.toString())
          js match {
            case Extractor.HandleInvite(a) => {
              if (profile.isEmpty) {
                newSignup(a.profile, Some(a.token))
                profile = Some(a.profile)
              }

              caches.invites(a.token) match {
                case Some(email) => {


                  caches.invites - a.token
                  master ! a
                }
                case _ => channel.push(withError("It seems that your challenge request has expired"))

              }


            }

            case Extractor.Find(f) => master ! f
            /*  (master.ask(f)(ctx.findTimeout)) onComplete {
                case Success(w: models.War) =>
                case Failure(e) => {
                  println(s"Find Failure ${e.getMessage}")
                  channel.push(e)
                }
                case _ =>
              }

            }  */
            case Extractor.Invite(r) => master.ask(r)(Timeout(30, SECONDS)).mapTo[String] onComplete {
              case Success(token) => {

                val feedback = caches.invites(token).map {
                  _ => withFeedback(s"Invite for ${r.email} has already been sent")
                } getOrElse {

                  caches.invites(token, r.email)

                  sendInviteEmail(profile.get, r.email, token)
                  withFeedback(s"A Challenge request has been sent out to ${r.email}")

                }

                channel push (feedback)
              }
              case Failure(e) => {

                log error("Invite Failure", e)
                channel push (Option(e.getCause).getOrElse(e))
              }
              // TODO add auto battle creation if user is currently on the site

            }
            case Extractor.ChallengeResponse(cr) => master ! cr


            case Extractor.WarAction(wa) => master ! wa
            case Extractor.Rematch(r) => master.ask(r)(Timeout(30, SECONDS)) onComplete {
              case Success(c: RematchContext) => {


                caches.invites(c.token, c.email)

                sendInviteEmail(profile.get, c.email, c.token)




                channel push (withFeedback(s"A Challenge request has been sent out to ${c.profile.name}"))
              }
              case _ => channel push withError("Unable to send a rematch request")

            }


            case x: Any => {
              log error (s"Received malformed json ${js.toString}")
              channel push withError("Malformed request, no points awarded")
            }


          }


      } map {
        _ => master ! Disconnect(profileId)
      }

      (in, out &> Concurrent.buffer(100))
  }

}
