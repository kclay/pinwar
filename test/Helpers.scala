import akka.util.Timeout

import com.rethinkscala.CurrentSchema
import com.typesafe.config.ConfigFactory
import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.Configuration
import play.api.test._
import play.api.test.FakeApplication
import scala.concurrent._
import scala.concurrent.duration._


/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 8/12/13
 * Time: 8:24 PM 
 */


trait Helpers {


  class TestApplication extends FakeApplication {

    override def configuration = {
      val c = super.configuration ++ Configuration(ConfigFactory.parseFileAnySyntax(new java.io.File(".", "conf/testing.conf")))
      c
    }


  }

  abstract class WithApplication(val app: FakeApplication = new TestApplication) extends Around with Scope {
    implicit def implicitApp = app


    def before: Unit = {}

    def after: Unit = {}

    override def around[T: AsResult](t: => T): Result = {


      play.api.test.Helpers.running(app)(AsResult.effectively({
        before

        CurrentSchema(Some(TestSchema))

        TestSchema.setup(TestSchema.connection)
        val value = t
        after
        value
      }))
    }
  }

  abstract class WithServer(val app: FakeApplication = FakeApplication(),
                            val port: Int = play.api.test.Helpers.testServerPort) extends Around with Scope {
    implicit def implicitApp = app

    implicit def implicitPort: Port = port

    override def around[T: AsResult](t: => T): Result = Helpers.running(TestServer(port, app))(AsResult.effectively({

      CurrentSchema(Some(TestSchema))
      t
    }))
  }

  def withValue[T](f: Future[T], timeout: Int): T = play.api.test.Helpers.await(f)(Timeout(timeout, SECONDS))

  def block(timeout: Long) = {
    val p = promise[Boolean]
    try {
      play.api.test.Helpers.await(p.future)(Timeout(timeout, SECONDS))
    } catch {
      case e: TimeoutException =>
    }
  }
}
