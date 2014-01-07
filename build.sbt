name := "kvstore"

version := "1.0.0"

scalaVersion := "2.10.2"

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "junit" % "junit" % "4.10" % "test"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.10.1"


libraryDependencies ++= Seq(
//    "com.netflix.rxjava" % "rxjava-scala" % "0.15.0",
    "org.json4s" % "json4s-native_2.10" % "3.2.5",
  //  "org.scala-lang" % "scala-swing" % "2.10.3",
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


