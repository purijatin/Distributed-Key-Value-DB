resolvers += "Spray Repository" at "http://repo.spray.cc/"

libraryDependencies += "net.databinder" %% "dispatch-http" % "0.8.8"

libraryDependencies += "org.scalastyle" %% "scalastyle" % "0.3.2"

libraryDependencies += "cc.spray" %%  "spray-json" % "1.1.1"

// need scalatest also as a build dependency: the build implements a custom reporter
libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1"

// dispatch uses commons-codec, in version 1.4, so we can't  go for 1.6.
// libraryDependencies += "commons-codec" % "commons-codec" % "1.4"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.1"

// sbteclipse-plugin uses scalaz-core 6.0.3, so we can't go 6.0.4
// libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.3"

scalacOptions ++= Seq("-deprecation")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.1")

// for dependency-graph plugin
// net.virtualvoid.sbt.graph.Plugin.graphSettings

