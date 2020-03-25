import com.typesafe.sbt.pgp.PgpKeys.{publishSigned, publishLocalSigned}

// Settings for entire build

enablePlugins(ScalaJSPlugin)

ThisBuild/version := "2.4.0-SNAPSHOT"

ThisBuild/organization := "org.bitbucket.inkytonik.kiama"

ThisBuild/scalaVersion := "2.13.1"
ThisBuild/crossScalaVersions := Seq("2.13.1")

ThisBuild/scalacOptions := {
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

ThisBuild/resolvers ++=
    Seq(
        Resolver.sonatypeRepo("releases"),
        Resolver.sonatypeRepo("snapshots")
    )

ThisBuild/incOptions := (ThisBuild/incOptions).value.withLogRecompileOnMacro(false)

ThisBuild/logLevel := Level.Info

ThisBuild/shellPrompt := {
    state =>
        Project.extract(state).currentRef.project + " " + version.value +
            " " + scalaVersion.value + "> "
}

ThisBuild/mainClass := None


def setupProject(project : Project, projectName : String) : Project =
    project.settings(
        name := projectName
    )

def setupSubProject(project : Project, projectName : String) : Project =
    setupProject(
        project,
        projectName
    ).enablePlugins(
      ScalaJSPlugin
    ).settings(
    )

val noPublishSettings =
    Seq(
        publish := {},
        publishLocal := {},
        publishSigned := {},
        publishLocalSigned := {}
    )

lazy val root =
    setupSubProject(
        project in file("core"),
        "kiama"
    ).settings(
        libraryDependencies ++= Seq(
          "org.scala-lang" % "scala-library" % scalaVersion.value
        ),
    )
