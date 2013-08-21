import java.io.File
import sbt._
import sbt.Keys._
import play.Project._
import sbt.File

import com.typesafe.sbt.SbtAtmos.{Atmos, atmosSettings}

object ApplicationBuild extends Build {

  val appName = "server"
  val appVersion = "0.6"


  val appResolvers = Seq(
    "Typesafe Repository2" at "http://typesafe.artifactoryonline.com/typesafe/",
    "Rhinofly Internal Release Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local",

    Resolver.file("LocalIvy", file(Path.userHome +
      File.separator + ".ivy2" + File.separator +
      "local"))(Resolver.ivyStylePatterns)
    //"Keyston Repository Releases" at "http://kclay.github.io/releases",
    //"Keyston Repository Snapshots" at "http://kclay.github.io/snapshots"
  )

  val akkaVersion = "2.2.0"


  val appDependencies = Seq(
    // Add your project dependencies here,
    cache,
    //"org.squeryl" %% "squeryl" % "0.9.5-6",
    //"mysql" % "mysql-connector-java" % "5.1.10",
    //"com.h2database" % "h2" % "1.2.127",
    "com.rethinkscala" %% "core" % "0.4.3-SNAPSHOT",
    "com.typesafe" %% "play-plugins-mailer" % "2.1.0",
    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
    "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion,


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
    routesImport += "binders._",
    resolvers ++= appResolvers,

    //routesImport ++= Seq("binders._", "models._", "models.Fight.Actions._"),
    routesImport ++= Seq("models._"),
    scalaVersion := "2.10.2",
    lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" ** "*.less")
  ).configs(Atmos)
    .settings(atmosSettings: _*)

  /*javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF-8"),
  javacOptions in doc := Seq("-source", "1.6")*

  )*/

}
