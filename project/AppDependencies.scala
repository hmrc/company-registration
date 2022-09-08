
import sbt._

object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._

  private val playVersion                 =  "-play-28"
  private val bootstrapPlayVersion        =  "7.2.0"
  private val domainVersion               = s"8.1.0$playVersion"
  private val scalaTestVersion            =  "3.2.12"
  private val scalatestPlusPlayVersion    =  "5.1.0"
  private val akkaVersion                 =  "1.9.2-akka-2.6.x"
  private val catsVersion                 =  "2.7.0"
  private val wiremockVersion             =  "2.27.2"
  private val hmrcMongoVersion            =  "0.71.0"
  private val flexmarkVersion             =  "0.62.2"
  private val hmrcTime                    =  "3.32.0"

  val compile = Seq(
    ws,
    "com.enragedginger"         %%  "akka-quartz-scheduler"         % akkaVersion,
    "uk.gov.hmrc"               %% s"bootstrap-backend$playVersion" % bootstrapPlayVersion,
    "uk.gov.hmrc"               %%  "domain"                        % domainVersion,
    "org.typelevel"             %%  "cats-core"                     % catsVersion,
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo$playVersion"        % hmrcMongoVersion
  )

  val test = Seq(
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo-test$playVersion"   % hmrcMongoVersion          % "test, it",
    "org.scalatest"             %%  "scalatest"                     % scalaTestVersion          % "test, it",
    "org.scalatestplus.play"    %%  "scalatestplus-play"            % scalatestPlusPlayVersion  % "test, it",
    "com.vladsch.flexmark"      %   "flexmark-all"                  % flexmarkVersion           % "test, it",
    "com.typesafe.play"         %%  "play-test"                     % PlayVersion.current       % "test, it",
    "org.scalatestplus"         %%  "mockito-4-5"                   % s"$scalaTestVersion.0"    % "test",
    "org.scalatestplus"         %%  "scalacheck-1-16"               % s"$scalaTestVersion.0"    % "test",
    "com.github.tomakehurst"    %   "wiremock-jre8"                 % wiremockVersion           % "it"
  )

  def apply() = compile ++ test
}

