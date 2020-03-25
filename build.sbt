import com.typesafe.sbt.pgp.PgpKeys.{publishSigned, publishLocalSigned}

import scalariform.formatter.preferences._

// Settings for entire build

enablePlugins(ScalaJSPlugin)

ThisBuild/version := "2.4.0-SNAPSHOT"

ThisBuild/organization := "org.bitbucket.inkytonik.kiama"

ThisBuild/scalaVersion := "2.13.0"
ThisBuild/crossScalaVersions := Seq("2.13.0")

ThisBuild/scalacOptions := {
    // Turn on all lint warnings, except:
    //  - stars-align: incorrectly reports problems if pattern matching of
    //    unapplySeq extractor doesn't match sequence directly
    val lintOption =
        if (scalaVersion.value.startsWith("2.13"))
            "-Xlint:-stars-align,-nonlocal-return,_"
        else
            "-Xlint:-stars-align,_"
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

// Common project settings

val commonSettings =
    Seq(
        unmanagedSourceDirectories in Compile ++= {
            val sourceDir = (sourceDirectory in Compile).value
            CrossVersion.partialVersion(scalaVersion.value) match {
                case Some((2, 13)) =>
                    Seq(sourceDir / "scala-2.not11", sourceDir / "scala-2.11+", sourceDir / "scala-2.13")
                case version =>
                    sys.error(s"unexpected Scala version $version")
            }
        },

        libraryDependencies :=
            Seq(
                "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
                "org.scalatest" %% "scalatest" % "3.1.0" % "test",
                "org.scalatestplus" %% "scalacheck-1-14" % "3.1.0.0" % "test"
            ),

        // Formatting
        scalariformPreferences := scalariformPreferences.value
            .setPreference(AlignSingleLineCaseStatements, true)
            .setPreference(DanglingCloseParenthesis, Force)
            .setPreference(IndentSpaces, 4)
            .setPreference(SpaceBeforeColon, true)
            .setPreference(SpacesAroundMultiImports, false),

        // Publishing
        publishTo := {
            val nexus = "https://oss.sonatype.org/"
            if (version.value.trim.endsWith("SNAPSHOT"))
                Some("snapshots" at nexus + "content/repositories/snapshots")
            else
                Some("releases" at nexus + "service/local/staging/deploy/maven2")
        },
        publishMavenStyle := true,
        Test/publishArtifact := true,
        pomIncludeRepository := { _ => false },
        pomExtra := (
            <url>https://bitbucket.org/inkytonik/kiama</url>
            <licenses>
                <license>
                    <name>Mozilla Public License, v. 2.0</name>
                    <url>http://mozilla.org/MPL/2.0/</url>
                    <distribution>repo</distribution>
                </license>
            </licenses>
            <scm>
                <url>https://bitbucket.org/inkytonik/kiama</url>
                <connection>scm:hg:https://bitbucket.org/inkytonik/kiama</connection>
            </scm>
            <developers>
                <developer>
                   <id>inkytonik</id>
                   <name>Tony Sloane</name>
                   <url>https://bitbucket.org/inkytonik</url>
                </developer>
            </developers>
        )
    )

val versionSettings =
    Seq(
        libraryDependencies ++= {
            CrossVersion.partialVersion(scalaVersion.value) match {
                case _ =>
                    Seq()
            }
        }
    )

// Project configuration:
//   - base project containing macros and code that they need
//   - core project containing main Kiama functionality, including its tests
//   - kiama (root) project aggregates base and core

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
        commonSettings : _*
    ).settings(
        versionSettings : _*
    )

def baseLibraryDependencies (scalaVersion : String) : Seq[ModuleID] = {
    Seq(
        // Caching (we have to get rid of this for scalajs)
        "com.google.guava" % "guava" % "21.0",
        // Reflection (used in Config)
        "org.scala-lang" % "scala-reflect" % scalaVersion
    )
}

val noPublishSettings =
    Seq(
        publish := {},
        publishLocal := {},
        publishSigned := {},
        publishLocalSigned := {}
    )

lazy val base =
    setupSubProject(
        project in file("base"),
        "base"
    ).settings(
        noPublishSettings : _*
    ).settings(
        libraryDependencies ++= baseLibraryDependencies(scalaVersion.value),
    )

lazy val core =
    setupSubProject(
        project in file("core"),
        "kiama"
    ).settings(
        libraryDependencies ++= baseLibraryDependencies(scalaVersion.value),

        console/initialCommands := """
            import org.bitbucket.inkytonik.kiama._
        """.stripMargin,
        Compile/packageBin/mappings := (Compile/packageBin/mappings).value ++ (base/Compile/packageBin/mappings).value,
        Compile/packageSrc/mappings := (Compile/packageSrc/mappings).value ++ (base/Compile/packageSrc/mappings).value,
    ).dependsOn(
        base % "compile-internal; test-internal"
    )

lazy val root =
    setupProject(
        project in file("."),
        "root"
    ).settings(
        noPublishSettings : _*
    ).aggregate(
        core
    )
