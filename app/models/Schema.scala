package models

import com.rethinkscala.net.{Connection, Version1}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.databind.module.SimpleModule


import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import com.fasterxml.jackson.core.JsonParser


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/15/13
 * Time: 9:12 AM 
 */


private class PinWarModule extends SimpleModule("PinWarModule") {

  class CategoryDeserializer extends JsonDeserializer[Category] {
    def deserialize(jp: JsonParser, ctxt: DeserializationContext) = {
      Category(jp.getText) getOrElse (Unknown)
    }
  }
 /*
  class PowerUpDeserializer extends JsonDeserializer[PowerUp] {
    def deserialize(jp: JsonParser, ctxt: DeserializationContext) = {
      PowerUp(jp.getText)
    }
  }   */

  addDeserializer(classOf[Category], new CategoryDeserializer)
//  addDeserializer(classOf[PowerUp], new PowerUpDeserializer)
}

object Schema extends com.rethinkscala.Schema {

  import play.api.Play.current
  import play.api.Play.configuration


  private val host = configuration.getString("rethink.default.url", None)
  private val version = Version1(host.getOrElse("localhost"), maxConnections = 20)
  implicit val connection = Connection(version)

  val profiles = table[Profile]("profiles")

  val wars = table[War]("wars")
  val signups = table[Signup]("signups")
  val points = table[Point]("points")
  val stats = table[Stats]

  val boards = table[Board]("boards")
  val likes = table[Like]("likes")
  val repins = table[Repin]("repins")
  val pins = table[Pin]("pins")
  val comments = table[Comment]("comments")


  override protected def defineMapper = {
    val mapper = super.defineMapper



    mapper.registerModule(new JodaModule)
    mapper.registerModule(new PinWarModule)
    mapper
  }
}
