package controllers

import play.api._
import play.api.mvc._
import play.api.templates.Html
import models.Stats
import actions.WithCors
import models.Schema._


object Application extends Controller with WithCors {

  def index = Action {
    Logger.debug("Testing")
    Ok(views.html.index("Your new application is ready."))
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

  def template(name: String) = AllowCors {
    implicit request =>

      var content = ""
      val templateName = "views.html.ajax." + name
      try {
        val c = Class.forName(templateName + "$")
        val tpl = c.getField("MODULE$").get(c).asInstanceOf[ {def apply()(implicit request: play.api.mvc.RequestHeader): Html}]
        content = tpl().toString()

      } catch {
        case e: Exception =>
          Logger.debug(e.getMessage)

      }
      Ok(content).withHeaders(
        "Access-Control-Allow-Origin" -> "*",
        "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
        "Access-Control-Allow-Headers" -> "Content-Type, X-Requested-With, Accept",
        // cache access control response for one day
        "Access-Control-Max-Age" -> (60 * 60 * 24).toString

      ).as("text/html")


  }

}