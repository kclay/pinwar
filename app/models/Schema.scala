package models

import com.rethinkscala.net.{Connection, Version1}
import com.fasterxml.jackson.datatype.joda.JodaModule


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/15/13
 * Time: 9:12 AM 
 */
object Schema extends com.rethinkscala.Schema {

  import play.api.Play.current
  import play.api.Play.configuration


  private val host = configuration.getString("rethink.default.url", None)
  private val version = Version1(host.getOrElse("localhost"))
  implicit val connection = Connection(version)

  val profiles = table[Profile]("profiles")

  val wars = table[War]("wars")
  val signups = table[Signup]("signups")
  val points = table[Point]("points")
  val stats = table[Stats]



  override protected def defineMapper = {
    val mapper = super.defineMapper

    mapper.registerModule(new JodaModule)
    mapper
  }
}
