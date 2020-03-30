name := "kiama-scalajs"
version := "2.4.0-SNAPSHOT"
organization := "org.bitbucket.inkytonik.kiama"

scalaVersion := "2.13.1"
scalacOptions := {
    // Turn on all lint warnings, except:
    //  - stars-align: incorrectly reports problems if pattern matching of
    //    unapplySeq extractor doesn't match sequence directly
    val lintOption =
            "-Xlint:-stars-align,-nonlocal-return,_"

    Seq(
        "-deprecation",
        "-feature",
        "-sourcepath", baseDirectory.value.getAbsolutePath,
        "-unchecked",
        "-Xcheckinit",
        "-Xfatal-warnings",
        lintOption
    )
}

libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-library" % scalaVersion.value
)

logLevel := Level.Info
mainClass := None

// Settings to publish to github packages.
// Remember to call sbt with the environment variable `GH_TOKEN=<SECRET>` set
//githubOwner := "b-studios"
//githubRepository := "kiama"

enablePlugins(ScalaJSPlugin)
