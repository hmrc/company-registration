import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object MicroServiceBuild extends Build with MicroService {

  val appName = "company-registration"

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin
  )

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._

  private val bootstrapPlayVersion = "5.1.0"
  private val domainVersion = "5.6.0-play-25"
  private val hmrcTestVersion = "3.9.0-play-25"
  private val reactiveMongoVersion = "7.22.0-play-25"
  private val mockitoVersion = "3.2.4"
  private val scalatestPlusPlayVersion = "2.0.0"
  private val mongoLockVersion = "6.18.0-play-25"
  private val authClientVersion = "2.32.1-play-25"

  val compile = Seq(
    ws,
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.0-akka-2.5.x",
    "uk.gov.hmrc" %% "bootstrap-play-25" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "mongo-lock" % mongoLockVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % reactiveMongoVersion,
    "org.typelevel" %% "cats" % "0.9.0",
    "uk.gov.hmrc" %% "auth-client" % authClientVersion
  )
  def tmpMacWorkaround(): Seq[ModuleID] =
    if (sys.props.get("os.name").exists(_.toLowerCase.contains("mac")))
      Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.17.1-osx-x86-64" % "runtime,test,it")
    else Seq()

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "3.0.0" % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.mockito" % "mockito-core" % mockitoVersion % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "4.15.0-play-25" % scope
      )
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "2.2.6" % scope,
        "org.pegdown" % "pegdown" % "1.5.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
        "com.github.tomakehurst" % "wiremock" % "2.6.0" % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest() ++ tmpMacWorkaround
}

