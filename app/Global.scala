
import com.rethinkscala.CurrentSchema
import play.api.{Application, GlobalSettings}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/15/13
 * Time: 11:02 AM 
 */
object Global extends GlobalSettings {


  override def onStart(app: Application) {

    CurrentSchema(None)
    //import models.Schema.connection
    //models.Schema.setup

    super.onStart(app)

  }
}
