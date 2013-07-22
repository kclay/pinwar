import java.io.File
import sbt._
import sbt.Keys._
import play.Project._
import sbt.File

object ApplicationBuild extends Build {

  val appName = "server"
  val appVersion = "1.0-SNAPSHOT"


  val appResolvers = Seq(
    "Typesafe Repository2" at "http://typesafe.artifactoryonline.com/typesafe/",
    Resolver.file("LocalIvy", file(Path.userHome +
      File.separator + ".ivy2" + File.separator +
      "local"))(Resolver.ivyStylePatterns)
  )

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    //"org.squeryl" %% "squeryl" % "0.9.5-6",
    //"mysql" % "mysql-connector-java" % "5.1.10",
    //"com.h2database" % "h2" % "1.2.127",
    "com.rethinkscala" %% "core" % "0.3-SNAPSHOT",
    "com.typesafe" %% "play-plugins-mailer" % "2.1.0",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.1.1"

  )

  /*
  /*.
    settings(cloudBeesSettings: _*)
    .settings(
    // Add your own project settings here
    CloudBees.applicationId := Some(appName)  */

   */

  val main = play.Project(appName, appVersion, appDependencies)
    .settings(
    requireJs ++= Seq(
      "battle/main.js"
    ),
    routesImport += "binders._",
    resolvers ++= appResolvers,

    //routesImport ++= Seq("binders._", "models._", "models.Fight.Actions._"),
    routesImport ++= Seq("models._"),

    lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" ** "*.less"),
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF-8"),
    javacOptions in doc := Seq("-source", "1.6")

  )

}
