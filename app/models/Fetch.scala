package models

import models.Schema._

import com.rethinkscala.Implicits._
import scala.reflect.ClassTag
import play.api.cache.{Cache => C}
import play.api.Play.current
import org.apache.commons.lang3.reflect.TypeUtils

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/31/13
 * Time: 6:21 PM 
 */
object Fetch {


  private def _boardByProfile(warId: String, profileId: String) = boards.filter(b => b \ "warId" === warId and b \ "profileId" === profileId)


  private def cast[T](item: Any)(implicit ct: ClassTag[T]): Option[T] = if (TypeUtils.isInstance(item, ct.runtimeClass)) Some(item.asInstanceOf[T]) else None

  /*

  def boardByProfile(warId: String, profileId: String) = Option(C.getOrElse[Board](s"board_${warId}_${profileId}", 60 * 2) {
    _boardByProfile(warId, profileId).as[Board].fold(e => null, _.headOption.getOrElse(null))
  })
    */
  def boardByProfile(warId: String, profileId: String) = _boardByProfile(warId, profileId).as[Board].fold(e => None, _.headOption)


  def boardCategory(warId: String, profileId: String) = C.getOrElse[Category](s"category_${warId}_${profileId}", 60 * 2) {
    boardByProfile(warId, profileId) map (_.category) getOrElse Unknown
  }

  /*
   def profile(id: String) = Option(C.getOrElse[Profile](s"profile_opt_$id", 0) {
     profiles.get(id).run fold(e => null, p => p)
   })*/
  def profile(id: String): Option[Profile] = profiles.get(id).toOpt


  lazy val store = new CacheStore


}

class CacheStore {

  case class InternalCache[T](prefix: String = "", filler: String => T, private val expire: Int = 0)(implicit ct: ClassTag[T]) {
    private def p(key: String) = s"${prefix}_${key.toString}"

    def get(key: String): T = C.getOrElse[T](p(key)) {
      filler(key)
    }

    def as[S](key: String)(implicit ct: ClassTag[S]) = C.getAs[S](key)

    def set(key: String, value: T, expiration: Int = expire) = C.set(p(key), value, expiration)

    def apply(key: String): Option[T] = as[T](key)

    def apply(key: String, value: T) = set(key, value)

    def -(key: String) = Fetch


  }

  lazy val profiles = new InternalCache[Profile]("profile", (id => Schema.profiles.get(id).run.right.get))

  lazy val invites = new InternalCache[String]("invite", (id => null), 30 * 60)


}