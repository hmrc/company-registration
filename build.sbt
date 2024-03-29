
import sbt.Keys.{javaOptions, parallelExecution, _}
import sbt._
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.bobby.SbtBobbyPlugin.BobbyKeys.bobbyRulesURL
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion


val appName = "company-registration"

lazy val scoverageSettings = {
  // Semicolon-separated list of regexs matching classes to exclude
  import scoverage.ScoverageKeys
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;model.*;config.*;.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin, SbtArtifactory)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(PlayKeys.playDefaultPort := 9973)
  .settings(scalaSettings: _*)
  .settings(scoverageSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(bobbyRulesURL := Some(new URL("https://webstore.tax.service.gov.uk/bobby-config/deprecated-dependencies.json")))
  .settings(
    scalacOptions += "-Xlint:-unused",
    targetJvm := "jvm-1.8",
    libraryDependencies ++= AppDependencies(),
    Test / parallelExecution := false,
    Test / fork := false,
    retrieveManaged := true,
    scalacOptions ++= List("-Xlint:-missing-interpolator"),
    resolvers += Resolver.jcenterRepo,
    scalaVersion := "2.13.8"
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings())
  .settings(majorVersion := 1)
  .settings(IntegrationTest / javaOptions += "-Dlogger.resource=logback-test.xml")
