package utils

import play.api.Application

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/14/13
 * Time: 2:33 PM 
 */
case object Cache {

  lazy val defaultProvider = new CacheProvider(play.api.Play.current)

  private var _current: Option[CacheProvider] = None

  def apply(app:Application):Unit = apply(Some(new CacheProvider(app)))
  def apply(provider: Option[CacheProvider]):Unit = _current = provider

  private def underlying = _current.getOrElse(defaultProvider)

  /**
   * Set a value into the cache.
   *
   * @param key Item key.
   * @param value Item value.
   * @param expiration Expiration time in seconds (0 second means eternity).
   */
  def set(key: String, value: Any, expiration: Int = 0) = underlying.set(key, value, expiration)

  /**
   * Set a value into the cache.
   *
   * @param key Item key.
   * @param value Item value.
   * @param expiration Expiration time as a [[scala.concurrent.duration.Duration]].
   */
  def set(key: String, value: Any, expiration: Duration) = underlying.set(key, value, expiration)

  /**
   * Retrieve a value from the cache.
   *
   * @param key Item key.
   */
  def get(key: String) = underlying.get(key)

  /**
   * Retrieve a value from the cache, or set it from a default function.
   *
   * @param key Item key.
   * @param expiration expiration period in seconds.
   * @param orElse The default function to invoke if the value was found in cache.
   */
  def getOrElse[A](key: String, expiration: Int = 0)(orElse: => A)(implicit ct: ClassTag[A]) = underlying.getOrElse[A](key, expiration)(orElse)

  /**
   * Retrieve a value from the cache for the given type
   *
   * @param key Item key.
   * @return result as Option[T]
   */
  def getAs[T](key: String)(implicit ct: ClassTag[T]) = underlying.getAs[T](key)

  def remove(key: String) = underlying.remove(key)
}