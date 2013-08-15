package utils

import play.api.Application

import play.api.cache.{CachePlugin, CacheAPI}
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import org.apache.commons.lang3.reflect.TypeUtils

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/14/13
 * Time: 2:27 PM 
 */
case class CacheProvider(app: Application) {

  implicit val thisApp = app

  private def cacheAPI: CacheAPI = {
    app.plugin[CachePlugin] match {
      case Some(plugin) => plugin.api
      case None => throw new Exception("There is no cache plugin registered. Make sure at least one CachePlugin implementation is enabled.")
    }
  }

  /**
   * Set a value into the cache.
   *
   * @param key Item key.
   * @param value Item value.
   * @param expiration Expiration time in seconds (0 second means eternity).
   */
  def set(key: String, value: Any, expiration: Int = 0): Unit = {
    cacheAPI.set(key, value, expiration)
  }

  /**
   * Set a value into the cache.
   *
   * @param key Item key.
   * @param value Item value.
   * @param expiration Expiration time as a [[scala.concurrent.duration.Duration]].
   */
  def set(key: String, value: Any, expiration: Duration): Unit = {
    set(key, value, expiration.toSeconds.toInt)
  }

  /**
   * Retrieve a value from the cache.
   *
   * @param key Item key.
   */
  def get(key: String): Option[Any] = {
    cacheAPI.get(key)
  }

  /**
   * Retrieve a value from the cache, or set it from a default function.
   *
   * @param key Item key.
   * @param expiration expiration period in seconds.
   * @param orElse The default function to invoke if the value was found in cache.
   */
  def getOrElse[A](key: String, expiration: Int = 0)(orElse: => A)(implicit ct: ClassTag[A]): A = {
    getAs[A](key).getOrElse {
      val value = orElse
      set(key, value, expiration)
      value
    }
  }

  /**
   * Retrieve a value from the cache for the given type
   *
   * @param key Item key.
   * @return result as Option[T]
   */
  def getAs[T](key: String)(implicit ct: ClassTag[T]): Option[T] = {
    get(key).map {
      item =>
        if (TypeUtils.isInstance(item, ct.runtimeClass)) Some(item.asInstanceOf[T]) else None
    }.getOrElse(None)
  }

  def remove(key: String) {
    cacheAPI.remove(key)
  }
}
