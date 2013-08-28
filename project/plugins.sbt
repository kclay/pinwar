// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

//resolvers += Resolver.file("Local repo", file("file://" + Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns)

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0-M2")

addSbtPlugin("com.typesafe.sbt" % "sbt-atmos" % "0.2.3")



