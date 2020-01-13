/*
 * Copyright 2019 HM Revenue & Customs
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

import auth.CryptoSCRS
import itutil.{IntegrationSpecBase, LoginStub, WiremockHelper}
import models.{Address, CorporationTaxRegistration, RegistrationStatus, TakeoverDetails}
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import repositories.CorporationTaxRegistrationMongoRepository

import scala.concurrent.ExecutionContext.Implicits.global

class TakeoverDetailsControllerISpec extends IntegrationSpecBase with LoginStub {

  val registrationId = "reg-12345"
  val internalId = "int-12345"
  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  private def client(path: String) = WS.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path").
    withFollowRedirects(false).
    withHeaders("Content-Type" -> "application/json")

  class Setup {
    val rmComp = app.injector.instanceOf[ReactiveMongoComponent]
    val crypto = app.injector.instanceOf[CryptoSCRS]
    val repository = new CorporationTaxRegistrationMongoRepository(
      rmComp, crypto)
    await(repository.drop)
    await(repository.ensureIndexes)

    def setupCTRegistration(reg: CorporationTaxRegistration): WriteResult = repository.insert(reg)
  }

  private def ctDoc(details: Option[TakeoverDetails] = None): CorporationTaxRegistration = CorporationTaxRegistration(
    internalId,
    registrationId,
    RegistrationStatus.DRAFT,
    formCreationTimestamp = "foo",
    language = "bar",
    takeoverDetails = details)

  val validTakeoverDetailsModel: TakeoverDetails = TakeoverDetails(
    businessName = "business",
    businessTakeoverAddress = Some(Address("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A"))),
    prevOwnersName = Some("human"),
    prevOwnersAddress = Some(Address("1 xyz", "2 xyz", Some("3 xyz"), Some("4 xyz"), Some("ZZ2 2ZZ"), Some("country B")))
  )

  val validTakeoverDetailsJson = Json.obj(
    "businessName" -> "business",
    "businessTakeoverAddress" -> Json.obj(
      "line1" -> "1 abc",
      "line2" -> "2 abc",
      "line3" -> "3 abc",
      "line4" -> "4 abc",
      "postcode" -> "ZZ1 1ZZ",
      "country" -> "country A"
    ),
    "prevOwnersName" -> "alien",
    "prevOwnersAddress" -> Json.obj(
      "line1" -> "space",
      "line2" -> "space",
      "line3" -> "space",
      "line4" -> "space",
      "postcode" -> "ZZ3 3ZZ",
      "country" -> "space"
    )
  )

  val invalidTakeoverDetailsJson = Json.obj(
    "businessName" -> true,
    "businessTakeoverAddress" -> Json.obj(
      "line1" -> "1 abc",
      "line2" -> "2 abc",
      "line3" -> "3 abc",
      "line4" -> "4 abc",
      "postcode" -> "ZZ1 1ZZ",
      "country" -> "country A"
    ),
    "prevOwnersName" -> "alien",
    "prevOwnersAddress" -> Json.obj(
      "line1" -> "space",
      "line2" -> "space",
      "line3" -> "space",
      "line4" -> "space",
      "postcode" -> "ZZ3 3ZZ",
      "country" -> "space"
    )
  )

  val unfilledTakeoverDetailsJson = Json.obj()

  s"GET /corporation-tax-registration/$registrationId/takeover-details" should {
    "successfully retrieve a json with a valid TakeoverDetails and a 200 status if the data exists" in new Setup {
      stubAuthorise(OK, "internalId" -> internalId)
      setupCTRegistration(ctDoc(Some(validTakeoverDetailsModel)))

      val response = await(client(s"/$registrationId/takeover-details").get())
      response.status shouldBe OK
    }
    "retrieve an empty json and a 204 response if the data is not found" in new Setup {
      stubAuthorise(OK, "internalId" -> internalId)
      setupCTRegistration(ctDoc())

      val response = await(client(s"/$registrationId/takeover-details").get())
      response.status shouldBe NO_CONTENT
    }
  }

  s"PUT /corporation-tax-registration/$registrationId/takeover-details" should {
    "return a 200 response with json body if the TakeoverDetails json is valid" in new Setup {
      stubAuthorise(OK, "internalId" -> internalId)
      setupCTRegistration(ctDoc(Some(validTakeoverDetailsModel)))

      val response = await(client(s"/$registrationId/takeover-details").put(validTakeoverDetailsJson))
      response.status shouldBe OK
      response.body shouldBe validTakeoverDetailsJson.toString()
    }
    "return a 400 response if the TakeoverDetails json is invalid" in new Setup {
      stubAuthorise(OK, "internalId" -> internalId)
      setupCTRegistration(ctDoc(Some(validTakeoverDetailsModel)))

      val response = await(client(s"/$registrationId/takeover-details").put(invalidTakeoverDetailsJson))
      response.status shouldBe BAD_REQUEST
    }
  }

}
