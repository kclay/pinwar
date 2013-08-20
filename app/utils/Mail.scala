package utils

import scala.util.{Failure, Success}
import play.api.Logger

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/16/13
 * Time: 11:39 AM 
 */
object Mail {

  import com.typesafe.plugin._
  import play.api.Play.current
  import scala.concurrent.future
  import play.api.libs.concurrent.Execution.Implicits._

  val mail = use[MailerPlugin].email

  def apply(to: String, subject: String, textHtml: String) = {


    future {
      mail.setSubject(subject)
      mail.addRecipient(to)
      mail.addFrom("BattleBot <no-reply@pinterestwar.com>")
      mail.sendHtml(textHtml)
    }
  } onComplete {
    case Success(c) => Logger.info(s"Email send = $c")
    case Failure(e) => Logger.error("Couldn't send email", e)
  }

}
