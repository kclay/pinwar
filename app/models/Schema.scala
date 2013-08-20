package models

import com.rethinkscala.net.{Connection, Version1}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.databind.module.SimpleModule


import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer}
import com.fasterxml.jackson.core.JsonParser
import com.rethinkscala.ast.Table
import com.rethinkscala.CurrentSchema


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


object Schema extends Schema("test") {


  def apply[T <: com.rethinkscala.net.Document](implicit mf: Manifest[T]): Table[T] = CurrentSchema.getOrElse(thisSchema).get[T].get
}


class Schema(dbName: String) extends com.rethinkscala.Schema {

  import play.api.Play.current
  import play.api.Play.configuration


  private val host = configuration.getString("rethink.default.url", None)
  private val version = Version1(host.getOrElse("127.0.0.1"), maxConnections = 20)
  implicit val connection = Connection(version)
  println(host)


  val DB = Some(dbName)
  val profiles = table[Profile]("profiles", db = DB)

  val wars = table[War]("wars", db = DB)
  val signups = table[Signup]("signups", db = DB)
  val points = table[Point]("points", db = DB)
  val stats = table[Stats]("stats", db = DB)

  val boards = table[Board]("boards", db = DB)
  val likes = table[Like]("likes")
  val repins = table[Repin]("repins", db = DB)
  val pins = table[Pin]("pins", db = DB)
  val comments = table[Comment]("comments", db = DB)

  val challenges = table[ChallengeToken]("challenge_tokens", db = DB)


  override protected def defineMapper = {
    val mapper = super.defineMapper



    mapper.registerModule(new JodaModule)
    mapper.registerModule(new PinWarModule)
    mapper
  }
}
