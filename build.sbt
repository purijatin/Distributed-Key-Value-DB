organization := "com.jatinpuri"

name := "kvstore"

version := "1.0.0"

scalaVersion := "2.10.2"

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "junit" % "junit" % "4.10" % "test"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.10.1"

libraryDependencies ++= Seq(
    "org.json4s" % "json4s-native_2.10" % "3.2.5",
    "net.databinder.dispatch" % "dispatch-core_2.10" % "0.11.0",
    "org.scala-lang" % "scala-reflect" % "2.10.3",
    "org.slf4j" % "slf4j-api" % "1.7.5",
    "org.slf4j" % "slf4j-simple" % "1.7.5",
    "com.squareup.retrofit" % "retrofit" % "1.0.0",
    "org.scala-lang.modules" %% "scala-async" % "0.9.0-M2"
     )

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.2.3",
    "com.typesafe.akka" %% "akka-testkit" % "2.2.3"
    )


publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/purijatin/Distributed-Key-Value-DB</url>
  <licenses>
    <license>
      <name>The MIT License (MIT)</name>
      <url>http://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:purijatin/Distributed-Key-Value-DB.git</url>
    <connection>scm:git:git@github.com:jsuereth/scala-arm.git</connection>
  </scm>
  <developers>
    <developer>
      <id>purijatin</id>
      <name>Jatin Puri</name>
      <url>http://purijatin@gmail.com</url>
    </developer>
  </developers>)
  
 credentials += Credentials(new java.io.File("/home/jatinpuri/.sbt/0.13/sonatype.credentials"))
 