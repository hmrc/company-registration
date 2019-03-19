/*
 * Copyright 2017 HM Revenue & Customs
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

import java.util.UUID

import auth.CryptoSCRS
import itutil.{IntegrationSpecBase, LoginStub, WiremockHelper}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsBoolean, JsObject, JsString, Json}
import play.api.libs.ws.WS
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.{CorporationTaxRegistrationMongoRepository, ThrottleMongoRepo}
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global

class ThrottleCheckISpec extends IntegrationSpecBase with LoginStub {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

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

  private def client(path: String) = WS.url(s"http://localhost:$port/company-registration$path").
    withFollowRedirects(false).
    withHeaders("Content-Type"->"application/json")

  class Setup {
    val rmComp = app.injector.instanceOf[ReactiveMongoComponent]
    val crypto = app.injector.instanceOf[CryptoSCRS]
    val crRepo = new CorporationTaxRegistrationMongoRepository(
      rmComp,crypto)
    await(crRepo.drop)
    await(crRepo.ensureIndexes)

    val throttleRepo = app.injector.instanceOf[ThrottleMongoRepo].repo
    await(throttleRepo.drop)
    await(throttleRepo.ensureIndexes)
  }

  "Company registration throttle / footprint check" should {

    val registrationId = UUID.randomUUID().toString
    val internalId = UUID.randomUUID().toString

    import reactivemongo.play.json._

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
         |"_id": "${DateTimeUtils.now.toString("yyyy-MM-dd")}",
         |"users_in": ${current},
         |"threshold": 0
         |}
       """.stripMargin).as[JsObject]

    "allow a user through if they already have a reg doc" in new Setup {
      stubAuthorise(internalId)

      val progress = "HO5"
      await(crRepo.collection.insert(crDoc(s""" "registrationProgress":"${progress}",""")))

      private val brURL = "/business-registration/business-tax-registration"
      stubGet(brURL, 200, s"""{"registrationID":"${registrationId}","formCreationTimestamp":"xxx", "language": "xxx"}""")
      stubPatch(s"${brURL}/last-signed-in/${registrationId}", 200, "")

      val response = client(s"/throttle/check-user-access").get.futureValue

      response.status shouldBe 200
      response.json shouldBe Json.obj(
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

      response.status shouldBe 200
      response.json shouldBe Json.obj(
        "registration-id" -> JsString(registrationId),
        "created" -> JsBoolean(true),
        "confirmation-reference" -> JsBoolean(false),
        "payment-reference" -> JsBoolean(false)
      )
    }

    "prevent a user through if we're at the limit" in new Setup {
      stubAuthorise(internalId)

      await(throttleRepo.collection.insert(throttleDoc(throttleThreshold)))

      private val brURL = "/business-registration/business-tax-registration"
      stubGet(brURL, 404, "")

      val progress = "HO5"
      val response = client(s"/throttle/check-user-access").get.futureValue

      response.status shouldBe 429
    }
  }
}
