import com.rethinkscala.net.{Version1, Connection}
import play.api.{Application, GlobalSettings}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/15/13
 * Time: 11:02 AM 
 */
object Global extends GlobalSettings {


  override def onStart(app: Application) {
    super.onStart(app)
    import models.Schema._
    models.Schema.setup
  }
}
