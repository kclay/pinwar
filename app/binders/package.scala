import models.Profile
import play.api.libs.json.Json
import play.api.mvc.{QueryStringBindable, PathBindable}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/16/13
 * Time: 10:38 AM 
 */
package object binders {

  import utils.Serialization.Reads.profileReads
  import utils.Serialization.Writes.profileWrites


  implicit def profileQueryPathBindable(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[Profile] {


    def bind(key: String, params: Map[String, Seq[String]]) = {
      for {
        json <- stringBinder.bind(key, params)
      } yield {
        json match {
          case Right(s) => Right(Json.parse(s).as[Profile])
          case _ => Left("Unable to build profile")
        }
      }
    }

    def unbind(key: String, value: Profile) = ""
  }

  implicit def profilePathBindable(implicit stringBinder: PathBindable[String]) = new PathBindable[Profile] {

    import models.Schema._

    def bind(key: String, value: String): Either[String, Profile] =
      for {
        id <- stringBinder.bind(key, value).right
        profile <- profiles.get(id).run.right.toOption.toRight("No profile found").right
      } yield profile

    def unbind(key: String, profile: Profile): String =
      stringBinder.unbind(key, profile.id)

  }
}
