package controllers

import scala.concurrent.duration._
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import battle._
import models.{Fetch, Signup}
import play.api.data._
import play.api.data.Forms._

import play.api.Play.current
import utils.{WatchedChannel, Mail}
import actions.WithCors
import play.api.cache.{Cached, Cache}


import battle.Connect
import scala.Some
import com.rethinkscala.net.RethinkNoResultsError
import battle.RematchContext
import play.api.libs.json.JsObject
import models.Profile
import battle.Disconnect
import com.rethinkscala.Implicits._


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


  def player(p: Profile) = Cached(s"player-${p.id}", 300) {
    AllowCors {
      implicit request =>
        var jsp = profileWrites.writes(p).asInstanceOf[JsObject]
        jsp = jsp +("rank", Json.toJson(p.rank))
        jsp = jsp +("stats", Json.toJson(p.stats))

        Ok(jsp)
    }
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

    log debug (s"New Signup Request profile = $profile , token = $token")
    val p = token.map(caches.invites(_).map(e => profile.copy(email = e))).flatten.getOrElse(profile)


    profiles.insert(p).run match {
      case Left(e) => BadRequest("Already registered")
      case Right(r) => if (r.inserted == 1) {


        ((signups insert Signup(profile.id, token.isDefined) withResults) run match {
          case Right(r) => if (token.isEmpty) r.returnedValue[Signup] map (sendSignupEmail(_, profile))
          else Some(Ok(""))


          case Left(e) => Some(BadRequest("Signup error"))
        }).get


      } else {
        BadRequest("Unable to create your profile")
      }

    }
  }

  def signup = AllowCors {
    implicit request =>
      profileForm.bindFromRequest fold(
        hasErrors => BadRequest("invalid"), {
        case (profile, token) => {
          val already = profiles.filter(v => v \ "id" === profile.id or v \ "id" === profile.email)
          already(0) run match {
            case Left(e) => newSignup(profile, token)
            case Right(p) => if (p.id == profile.id) {
              (signups get profile.id run).fold(x => BadRequest("Can't validate signup"), s => {
                if (s.activated) Ok("activated") else sendSignupEmail(s, profile)

              })
            } else BadRequest("Account already registered")
          }
        }

      })


  }


  val signupEvent = Json.obj("event" -> "signup",
    "data" -> Json.obj())

  def index(profileId: String, fromInvite: Boolean) = WebSocket.using[JsValue] {
    implicit request =>
    // new client


      implicit val timeout = Timeout(20, SECONDS)

      val (out, channel) = Concurrent.broadcast[JsValue]

      val watchedChannel = new WatchedChannel(channel, ctx.system)

      var profile = Fetch.profile(profileId)


      master ! Connect(profileId, watchedChannel, fromInvite)


      val in = Iteratee.foreach[JsValue] {
        js =>
          log info (js.toString())
          js match {
            case Extractor.HandleInvite(a) => {
              if (profile.isEmpty) {
                val result = newSignup(a.profile, Some(a.token))

                result.header.status match {
                  case OK => watchedChannel push signupEvent
                  case _ =>
                }

                profile = Some(a.profile)
              } else {
                watchedChannel push signupEvent
              }

              caches.invites(a.token) match {
                case Some(_) => {


                  caches.invites - a.token
                  master ! a
                }
                case _ => watchedChannel.push(withError("It seems that your challenge request has expired"))

              }


            }

            case Extractor.Find(f) => master ! f /*)(ctx.findTimeout)) onFailure {
              case e =>
                println(s"Find Failure ${e.getMessage}")
                watchedChannel.push(e)
            }     */


            case Extractor.Invite(r) => master.ask(r)(Timeout(30, SECONDS)).mapTo[String] onComplete {
              case Success(token) => {

                val feedback = caches.invites(token).map {
                  _ => withFeedback(s"Invite for ${r.email} has already been sent")
                } getOrElse {

                  caches.invites(token, r.email)

                  sendInviteEmail(profile.get, r.email, token)
                  withFeedback(s"A Challenge request has been sent out to ${r.email}")

                }

                watchedChannel push (feedback)
              }

              case Failure(e) => {

                log error("Invite Failure", e)
                watchedChannel push (Option(e.getCause).getOrElse(e))
              }
              // TODO add auto battle creation if user is currently on the site

            }
            case Extractor.ChallengeResponse(cr) => master ! cr


            case Extractor.WarAction(wa) => master ! wa
            case Extractor.Rematch(r) => master.ask(r)(Timeout(30, SECONDS)) onComplete {
              case Success(c: RematchContext) => {


                caches.invites(c.token, c.email)

                sendInviteEmail(profile.get, c.email, c.token)




                watchedChannel push (withFeedback(s"A Challenge request has been sent out to ${c.profile.name}"))
              }
              case _ => watchedChannel push withError("Unable to send a rematch request")

            }


            case x: Any => {
              log error (s"Received malformed json ${js.toString}")
              watchedChannel push withError("Malformed request, no points awarded")
            }


          }


      } map {
        _ => {
          master ! Disconnect(profileId)
          watchedChannel.done
        }
      }

      (in, out &> Concurrent.buffer(100))
  }

}
