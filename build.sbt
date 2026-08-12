organization := "danburkert"

name := "continuum"

version := "0.5.0"

versionScheme := Some("early-semver")

scalaVersion := "2.13.18"

crossScalaVersions := Seq("2.13.18", "3.3.7")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.19.0" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.20" % "test"

libraryDependencies += "org.scalatestplus" %% "scalacheck-1-19" % "3.2.20.0" % "test"
