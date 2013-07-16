package controllers

import play.api._
import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def template(name: String) = Action {
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
      )


  }

}