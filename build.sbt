organization := "danburkert"

name := "continuum"

version := "0.4-SNAPSHOT"

scalaVersion := "2.13.17"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.18.1" % "provided"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % "test"

libraryDependencies += "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % "test"
