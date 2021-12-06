
import sbt._

object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._

  private val bootstrapPlayVersion = "5.16.0"
  private val domainVersion = "6.2.0-play-28"
  private val reactiveMongoVersion = "8.0.0-play-28"
  private val mockitoVersion = "3.9.0"
  private val scalatestPlusPlayVersion = "3.1.3"
  private val mongoLockVersion = "7.0.0-play-28"
  private val authClientVersion = "5.6.0-play-28"


  val compile = Seq(
    ws,
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.9.2-akka-2.6.x",
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "mongo-lock" % mongoLockVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % reactiveMongoVersion,
    "org.typelevel" %% "cats" % "0.9.0",
    "uk.gov.hmrc" %% "auth-client" % authClientVersion,
    "com.typesafe.play" %% "play-json-joda" % "2.6.10"

  )

  def tmpMacWorkaround(): Seq[ModuleID] =
    if (sys.props.get("os.name").exists(_.toLowerCase.contains("mac")))
      Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.17.1-osx-x86-64" % "runtime,test,it")
    else Seq()

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.mockito" % "mockito-core" % mockitoVersion % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "5.0.0-play-28" % scope
      )
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
        "com.github.tomakehurst" % "wiremock-jre8" % "2.27.2" % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest() ++ tmpMacWorkaround
}

