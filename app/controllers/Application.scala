package controllers

import play.api._
import play.api.mvc._
import play.api.templates.Html
import models.{Profile, Stats}
import actions.WithCors
import models.Schema._
import play.api.cache.Cached
import utils.Mail
import play.api.data.Form
import play.api.data.Forms._
import models.Stats
import models.Profile
import play.api.libs.json.Json


object Application extends Controller with WithCors {

  import play.api.Play.current
  import play.api.Play.configuration

  val chromeExtensionId = configuration.getString("chrome.extensionId").get

  def index = Action {
    Logger.debug("Testing")
    Ok(views.html.index("Your new application is ready."))
  }

  val bugReportForm = Form(
    tuple(
      "title" -> text,
      "details" -> text,
      "session" -> text
    )
  )

  def reportError(profile: Profile) = AllowCors {
    implicit request =>
      bugReportForm.bindFromRequest.fold(
      hasErrors => BadRequest("Not saved"), {
        case (title, details, session) => {
          val builder = new StringBuilder
          builder.append("<strong>From :</strong>").append(profile.name).append("(").append(profile.id).append(" at ").append(profile.email)
            .append("<br/>").append("<strong>Title :</strong><br/>").append(title)
            .append("<br/><br/>").append("<strong>Details :</strong><br/>")
            .append(details).append("<br/><br/>")
            .append("<strong>Session :</strong></br>").append(Json.prettyPrint(Json.parse(session)))


          Mail("bugs@pinterestwar.com", "New Bug", builder.toString())
          Ok("Sent")
        }
      }
      )


  }

  def stat = Action {

    profiles.run match {
      case Left(e) => println(e)
      case Right(r) => stats.insert(r.map {
        p => Stats(p.id)
      }).run match {
        case Left(e) => println(e)
        case Right(r) => println(r)
      }

    }


    Ok("ok")
  }

  def template(name: String) = //Cached("template") {
    AllowCors {
      implicit request =>

        implicit val conf = configuration
        var content = ""
        val templateName = "views.html.ajax." + name
        try {
          val c = Class.forName(templateName + "$")
          val tpl = c.getField("MODULE$").get(c).asInstanceOf[ {def apply()(implicit request: play.api.mvc.RequestHeader, conf: Configuration): Html}]
          content = tpl().toString()

        } catch {
          case e: Exception =>
            Logger.debug(e.getMessage)

        }
        Ok(content).withHeaders(
          "Access-Control-Allow-Origin" -> "*",
          "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
          "Access-Control-Allow-Headers" -> "Content-Type, X-Requested-With, Accept"
          // cache access control response for one day
          //"Access-Control-Max-Age" -> (60 * 60 * 24).toString

        ).as("text/html")


      // }
    }

}