
import play.routes.compiler.InjectedRoutesGenerator

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._
  import sbt.Keys._
  import sbt._
  import uk.gov.hmrc.SbtAutoBuildPlugin
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
  import uk.gov.hmrc.versioning.SbtGitVersioning
  import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion


  val appName: String

  lazy val appDependencies: Seq[ModuleID] = ???
  lazy val plugins: Seq[Plugins] = Seq(play.sbt.PlayScala)
  lazy val playSettings: Seq[Setting[_]] = Seq.empty

  lazy val scoverageSettings = {
    // Semicolon-separated list of regexs matching classes to exclude
    import scoverage.ScoverageKeys
    Seq(
      ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;model.*;config.*;.*(AuthService|BuildInfo|Routes).*",
      ScoverageKeys.coverageMinimum := 80,
      ScoverageKeys.coverageFailOnMinimum := false,
      ScoverageKeys.coverageHighlighting := true
    )
  }
  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
    .settings(playSettings ++ scoverageSettings: _*)
    .settings(scalaSettings: _*)
    .settings(scoverageSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      targetJvm := "jvm-1.8",
      libraryDependencies ++= appDependencies,
      dependencyOverrides := MicroServiceBuild.overrides,
      parallelExecution in Test := false,
      fork in Test := true,
      retrieveManaged := true,
      evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
      scalacOptions ++= List(
        "-Xlint:-missing-interpolator"
      ),
      resolvers += Resolver.jcenterRepo,
      scalaVersion := "2.11.11"
    )
    .configs(IntegrationTest)
    .settings(integrationTestSettings())
    .settings(majorVersion := 1)
    .settings(javaOptions in IntegrationTest += "-Dlogger.resource=logback-test.xml")
}


