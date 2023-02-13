/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package api

import itutil.WiremockHelper._
import itutil.{IntegrationSpecBase, LoginStub, MongoIntegrationSpec, WiremockHelper}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsBoolean, JsObject, JsString, Json}
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, ThrottleMongoRepository}
import uk.gov.hmrc.http.{HeaderNames => GovHeaderNames}

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class ThrottleCheckISpec extends IntegrationSpecBase with MongoIntegrationSpec with LoginStub {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val throttleThreshold = 5

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.business-registration.port" -> s"$mockPort",
    "microservice.services.throttle-threshold" -> throttleThreshold.toString
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  private def client(path: String) = ws.url(s"http://localhost:$port/company-registration$path").
    withFollowRedirects(false).
    withHttpHeaders(
      "Content-Type" -> "application/json",
      GovHeaderNames.authorisation -> "Bearer123"
    )

  class Setup {
    val crRepo = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    crRepo.deleteAll
    await(crRepo.ensureIndexes)

    val throttleRepo = app.injector.instanceOf[ThrottleMongoRepository]
    throttleRepo.deleteAll
    await(throttleRepo.ensureIndexes)
  }

  "Company registration throttle / footprint check" must {

    val registrationId = UUID.randomUUID().toString
    val internalId = UUID.randomUUID().toString

    def crDoc(progressJson: String = "") = Json.parse(
      s"""
         |{
         |"internalId":"${internalId}","registrationID":"${registrationId}","status":"draft","formCreationTimestamp":"testDateTime","language":"en",
         |${progressJson}
         |"createdTime":1488304097470,"lastSignedIn":1488304097486
         |}
      """.stripMargin).as[JsObject]

    def throttleDoc(current: Int) = Json.parse(
      s"""
         |{
         |"_id": "${LocalDate.now}",
         |"users_in": ${current},
         |"threshold": 0
         |}
       """.stripMargin).as[JsObject]

    "allow a user through if they already have a reg doc" in new Setup {
      stubAuthorise(internalId)

      val progress = "HO5"
      crRepo.insertRaw(crDoc(s""" "registrationProgress":"${progress}","""))

      private val brURL = "/business-registration/business-tax-registration"
      stubGet(brURL, 200, s"""{"registrationID":"${registrationId}","formCreationTimestamp":"xxx", "language": "xxx"}""")
      stubPatch(s"${brURL}/last-signed-in/${registrationId}", 200, "")

      val response = client(s"/throttle/check-user-access").get.futureValue

      response.status mustBe 200
      response.json mustBe Json.obj(
        "registration-id" -> JsString(registrationId),
        "created" -> JsBoolean(false),
        "confirmation-reference" -> JsBoolean(false),
        "payment-reference" -> JsBoolean(false),
        "registration-progress" -> JsString(progress)
      )
    }

    "allow a user through if they're the first of the day" in new Setup {
      stubAuthorise(internalId)

      private val brURL = "/business-registration/business-tax-registration"
      stubGet(brURL, 404, "")
      stubPost(brURL, 200, s"""{"registrationID":"${registrationId}","formCreationTimestamp":"xxx", "language": "xxx"}""")
      stubPatch(s"${brURL}/last-signed-in/${registrationId}", 200, "")

      val progress = "HO5"
      val response = client(s"/throttle/check-user-access").get.futureValue

      response.status mustBe 200
      response.json mustBe Json.obj(
        "registration-id" -> JsString(registrationId),
        "created" -> JsBoolean(true),
        "confirmation-reference" -> JsBoolean(false),
        "payment-reference" -> JsBoolean(false)
      )
    }

    "prevent a user through if we're at the limit" in new Setup {
      stubAuthorise(internalId)

      throttleRepo.insertRaw(throttleDoc(throttleThreshold))

      private val brURL = "/business-registration/business-tax-registration"
      stubGet(brURL, 404, "")

      val progress = "HO5"
      val response = client(s"/throttle/check-user-access").get.futureValue

      response.status mustBe 429
    }
  }
}
