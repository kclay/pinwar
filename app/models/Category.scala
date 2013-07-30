package models

import com.fasterxml.jackson.annotation.{JsonCreator, JsonValue}

/**
 * Created by IntelliJ IDEA.
 * User: Keyston
 * Date: 7/11/13
 * Time: 4:01 PM 
 */

object Category {

  lazy val all = Seq(Popular, Everything, Gifts, Videos, Animals, Architecture, Art,
    CarsMotorcycles, Celebrities, DIYCrafts, Design, Education, FilmMusicBooks, Gardening,
    Geek, HairBeauty, HealthFitness, History, HolidaysEvents, HomeDecor, Humor, IllustrationsPosters, Kids, MensFashion,
    Outdoors, Photography, Products, Quotes, ScienceNature, Sports, Tattoos, Technology, Travel, Weddings, WomensFashion)

  def apply(name: String) = all.find(_.name == name)

}

case class A(f: String, b: String)

trait Category {

  import utils.StringHelper.lowerCaseWithUnderscore

  @JsonCreator
  def apply(name: String) = Category.all.find(_.name == name)

  private lazy val className = getClass.getSimpleName.replace("$", "")

  lazy val name: String = lowerCaseWithUnderscore(className)

  def unapply(n: Category) = if (n.equals(name)) Some(name) else None

  def displayName: String = className

  @JsonValue
  override def toString = name
}


case object Popular extends Category

case object Everything extends Category

case object Gifts extends Category

case object Videos extends Category

case object Animals extends Category

case object Architecture extends Category

case object Art extends Category

case object CarsMotorcycles extends Category {
  override def displayName = "Cars & Motorcycles"
}

case object Celebrities extends Category

case object DiyCrafts extends Category {
  override def displayName = "DIY & Crafts"
}

case object Design extends Category

case object Education extends Category


case object FilmMusicBooks extends Category {
  override def displayName = "Film, Music & Books"
}

case object Gardening extends Category

case object Geek extends Category

case object HairBeauty extends Category {
  override def displayName = "Hair & Beauty"
}

case object HealthFitness extends Category {
  override def displayName = "Health & Fitness"
}

case object History extends Category


case object HolidaysEvents extends Category {
  override def displayName = "Holidays & Events"
}

case object HomeDecor extends Category {
  override def displayName = "Home Decor"
}

case object Humor extends Category

case object IllustrationsPosters extends Category {
  override def displayName = "Illustrations & Posters"
}

case object Kids extends Category

case object MensFashion extends Category {
  override def displayName = "Men's Fashion"
}

case object Outdoors extends Category

case object Photography extends Category

case object Products extends Category

case object Quotes extends Category

case object ScienceNature extends Category {
  override def displayName = "Science Nature"
}


case object Sports extends Category

case object Tattoos extends Category


case object Technology extends Category

case object Travel extends Category

case object Weddings extends Category

case object WomensFashion extends Category {
  override def displayName = "Women's Fashion"
}
