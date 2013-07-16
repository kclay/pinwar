package utils

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/16/13
 * Time: 11:39 AM 
 */
object Mail {

  import com.typesafe.plugin._
  import play.api.Play.current

  val mail = use[MailerPlugin].email

  def apply(to: String, subject: String, textHtml: String) = {

    mail.setSubject(subject)
    mail.addRecipient(to)
    mail.addFrom("pinwar@no-reply.com")
    mail.sendHtml(textHtml)
  }

}
