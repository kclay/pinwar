package test

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
class IntegrationSpec extends Specification {

  import play.api.libs.json._
  import battle._

  /**
  "Application" should {
    
    "work from within a browser" in {
      running(TestServer(3333), HTMLUNIT) { browser =>

        browser.goTo("http://localhost:3333/")

        browser.pageSource must contain("Your new application is ready.")
       
      }
    }
    
  }   **/
 /*
  "Battle" should {

    import battle.Extractor._

    "marshall find event " in {
      val js = Json.obj(
        "event" -> "find",
        "data" -> Json.obj(
          "profileId" -> "foo"
        )

      )

      (js match {
        case Find(_) => true
        case _ => false
      }) must beTrue
    }

    "marshall war event" in {
      val js = Json.obj(
        "event" -> "war_action",
        "data" -> Json.obj(
          "profileId" -> "foo",
          "war" -> "bar",
          "action" -> Json.obj(
            "id" -> "a",
            "name" -> "b",
            "category" -> "gifts",
            "url" -> "http://googe.com"


          )

        )
      )
      (js match {
        case WarAction(_) => true
        case _ => false
      }) must beTrue
    }
  }     */


}