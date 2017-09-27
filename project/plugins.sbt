// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers ++= Seq(
  Classpaths.sbtPluginReleases,
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
)

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.3")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.1.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")
