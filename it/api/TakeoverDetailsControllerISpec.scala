/*
 * Copyright 2024 HM Revenue & Customs
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

import config.LangConstants
import itutil.WiremockHelper._
import itutil.{IntegrationSpecBase, LoginStub, MongoIntegrationSpec, WiremockHelper}
import models.{Address, CorporationTaxRegistration, RegistrationStatus, TakeoverDetails}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.Json
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.http.{HeaderNames => GovHeaderNames}

import scala.concurrent.ExecutionContext.Implicits.global

class TakeoverDetailsControllerISpec extends IntegrationSpecBase with MongoIntegrationSpec with LoginStub {

  val registrationId = "reg-12345"
  val internalId = "int-12345"
  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  private def client(path: String) = ws.url(s"http://localhost:$port/company-registration/corporation-tax-registration$path").
    withFollowRedirects(false).
    withHttpHeaders(
      "Content-Type" -> "application/json",
      GovHeaderNames.authorisation -> "Bearer123"
    )

  class Setup {
    val repository = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    repository.deleteAll
    await(repository.ensureIndexes)

    def setupCTRegistration(reg: CorporationTaxRegistration) = repository.insert(reg)
  }

  private def ctDoc(details: Option[TakeoverDetails] = None): CorporationTaxRegistration = CorporationTaxRegistration(
    internalId,
    registrationId,
    RegistrationStatus.DRAFT,
    formCreationTimestamp = "testTimestamp",
    language = LangConstants.english,
    takeoverDetails = details)

  val validTakeoverDetailsModel: TakeoverDetails = TakeoverDetails(
    replacingAnotherBusiness = true,
    businessName = Some("business"),
    businessTakeoverAddress = Some(Address("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A"))),
    prevOwnersName = Some("human"),
    prevOwnersAddress = Some(Address("1 xyz", "2 xyz", Some("3 xyz"), Some("4 xyz"), Some("ZZ2 2ZZ"), Some("country B")))
  )

  val validTakeoverDetailsJson = Json.obj(
    "replacingAnotherBusiness" -> true,
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
    "repladingAnotherBusiness" -> true,
    "businessName" -> 123,
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

  s"GET /corporation-tax-registration/$registrationId/takeover-details" must {
    "successfully retrieve a json with a valid TakeoverDetails and a 200 status if the data exists" in new Setup {
      stubAuthorise(OK, "internalId" -> internalId)
      setupCTRegistration(ctDoc(Some(validTakeoverDetailsModel)))

      val response = await(client(s"/$registrationId/takeover-details").get())
      response.status mustBe OK
    }
    "retrieve an empty json and a 204 response if the data is not found" in new Setup {
      stubAuthorise(OK, "internalId" -> internalId)
      setupCTRegistration(ctDoc())

      val response = await(client(s"/$registrationId/takeover-details").get())
      response.status mustBe NO_CONTENT
    }
  }

  s"PUT /corporation-tax-registration/$registrationId/takeover-details" must {
    "return a 200 response with json body if the TakeoverDetails json is valid" in new Setup {
      stubAuthorise(OK, "internalId" -> internalId)
      setupCTRegistration(ctDoc(Some(validTakeoverDetailsModel)))

      val response = await(client(s"/$registrationId/takeover-details").put(validTakeoverDetailsJson))
      response.status mustBe OK
      response.body mustBe validTakeoverDetailsJson.toString()
    }
    "return a 400 response if the TakeoverDetails json is invalid" in new Setup {
      stubAuthorise(OK, "internalId" -> internalId)
      setupCTRegistration(ctDoc(Some(validTakeoverDetailsModel)))

      val response = await(client(s"/$registrationId/takeover-details").put(invalidTakeoverDetailsJson))
      response.status mustBe BAD_REQUEST
    }
  }

}
