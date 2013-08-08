package battle

import play.api.libs.json._
import play.api.libs.iteratee.Concurrent.Channel
import models._
import akka.actor._
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import akka.pattern.ask


import play.api.cache.{EhCachePlugin, CachePlugin}
import play.api.Play.current
import akka.util.Timeout
import scala.Some
import net.sf.ehcache.event.CacheEventListener
import net.sf.ehcache.{Element, Ehcache}
import utils.Master


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 10:49 PM 
 */
case class ChannelContext(actorPath: ActorPath, pending: Boolean = false, blacklisted: Option[Cancellable] = None) {


  def !(message: JsValue)(implicit system: ActorSystem) = system.actorSelection(actorPath) ! message

  def !(message: AnyRef)(implicit system: ActorSystem) = system.actorSelection(actorPath) ! message

  def available = !pending && blacklisted.isEmpty

  def destroy = blacklisted map (_.cancel())

}

object BattleConfig {
  lazy val pointsNeededToWin = play.api.Play.configuration(play.api.Play.current).getInt("points.toWin").get

}

object BattleField {

  import play.api.Play.current


  val instance = new BattleField

  lazy val caches = instance.caches


  current.plugin[CachePlugin] match {
    case Some(p) => {
      val cache = p.asInstanceOf[EhCachePlugin].cache
      cache.getCacheEventNotificationService.registerListener(new CacheEventListener() {
        def notifyElementRemoved(p1: Ehcache, p2: Element) {}

        def notifyElementPut(p1: Ehcache, p2: Element) {}

        def notifyElementUpdated(p1: Ehcache, p2: Element) {}

        def notifyElementExpired(p1: Ehcache, p2: Element) {

          if (p2.getObjectKey.toString.startsWith("invite_")) {
            instance.invites.remove(p2.getObjectValue.asInstanceOf[String])
          }
        }

        def notifyElementEvicted(p1: Ehcache, p2: Element) {}

        def notifyRemoveAll(p1: Ehcache) {}

        def dispose() {}
      })
    }
    case _ =>
  }

}

class BattleField {


  lazy val caches = new CacheStore
  val timeoutThreshold = 5
  val findTimeout = Timeout((60 * 2) + timeoutThreshold, SECONDS)


  val inviteTimeout = Timeout(5, MINUTES)
  val system = Akka.system

  val sub = new TrenchSub(this)

  type ProfileId = String
  type Email = String

  val invites = mutable.Map.empty[String, (ProfileId, Email)]
  val invitesIds = ArrayBuffer.empty[String]

  val activeWars = mutable.Map.empty[String, String]

  val challengeTokens = mutable.Map.empty[String, Finder]
  val pendingFinders = mutable.ArrayBuffer.empty[Finder]


  val finders = mutable.ArrayBuffer.empty[Finder]


  val trench = new Trench(this)

  trench.subscribe(sub)

  def challengeTokenFor(f: Finder) = {

    val token = UUID.randomUUID().toString
    challengeTokens += (token -> f)
    token
  }

  def find(creator: Profile, sender: ActorRef, timeout: Timeout) = {


    val finder = Finder(this, creator, sender, timeout)
    pendingFinders.append(finder)
    finder


  }


  def actorPath(name: String) = (ActorPath.fromString(
    "akka://%s/user/%s".format(system.name, name)))


  def battleRef(id: String) = system.actorSelection(actorPath(s"war_$id"))

  def worker(name: String) = system.actorOf(Props(
    new BattleFieldWorker(actorPath("battle_field"))), name = name)

  private val numOfWorkers = 1
  lazy val master = {
    val _master = system.actorOf(Props[Master], name = "battle_field")
    (0 to numOfWorkers).foreach(x => worker(s"worker_${x + 1}"))
    _master
  }


}


/*
class Master extends utils.Master {

  import context._

  var connections: ActorRef = _

  override def preStart() {
    connections = actorOf(Props(new ConnectionSupervisor(BattleField.instance)), name = "connection")
    watch(connections)

  }

  override def receive = {

    case c: Connect => {
      connections ! c
    }

    case Terminated(a) => {
      log.error(s"Actor terminated $a")
    }

    case x: Any => super.receive(x)


  }
}
      */

abstract class AppError(val kind: String) {
  val message: String
}


case class DisconnectError(profile: Profile) extends AppError("disconnect") {
  lazy val message = s"${profile.name} has disconnected"
}






