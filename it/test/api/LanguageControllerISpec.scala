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

package test.api

import config.LangConstants
import models.{CorporationTaxRegistration, Language}
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository
import test.itutil.WiremockHelper.{stubAudit, stubAuthorise, stubUnauthorised}
import test.itutil.{IntegrationSpecBase, LoginStub, MongoIntegrationSpec, WiremockHelper}
import uk.gov.hmrc.http.{HeaderNames => GovHeaderNames}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class LanguageControllerISpec extends IntegrationSpecBase with LoginStub with MongoIntegrationSpec {

  val registrationId: String = UUID.randomUUID().toString
  val internalId: String = UUID.randomUUID().toString

  val ctReg: CorporationTaxRegistration = CorporationTaxRegistration(
    internalId = internalId,
    registrationID = registrationId,
    status = "draft",
    formCreationTimestamp = "testDateTime",
    language = LangConstants.english,
    createdTime = Instant.now().minus(1, ChronoUnit.DAYS),
    lastSignedIn = Instant.now(),
  )

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val additionalConfiguration: Map[String, String] = Map(
    "auditing.consumer.baseUri.port" -> s"${WiremockHelper.wiremockPort}",
    "microservice.services.auth.port" -> s"${WiremockHelper.wiremockPort}"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  private def client =
    ws.url(s"http://localhost:$port/company-registration/corporation-tax-registration/$registrationId/language")
      .withFollowRedirects(false)
      .withHttpHeaders(
        "Content-Type" -> "application/json",
        GovHeaderNames.authorisation -> "Bearer123"
      )

  class Setup {
    val repository: CorporationTaxRegistrationMongoRepository = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    repository.deleteAll
    await(repository.ensureIndexes)
    stubAudit()
  }

  s"GET /company-registration/corporation-tax-registration/$registrationId/language" must {

    "Return OK (200) and the stored Language code when a document exists" in new Setup {

      stubAuthorise(200, "internalId" -> internalId)
      repository.insert(ctReg)

      val response: WSResponse = client.get().futureValue

      response.status mustBe Status.OK
      response.json mustBe Json.toJson(Language(LangConstants.english))
    }

    "Return NOT_FOUND (404) when no document exists" in new Setup {

      stubAuthorise(200, "internalId" -> internalId)

      val response: WSResponse = client.get().futureValue

      response.status mustBe Status.NOT_FOUND
    }

    "Return UNAUTHORISED (401) when user is not signed in" in new Setup {

      stubUnauthorised("BearerTokenExpired")

      val response: WSResponse = client.get().futureValue

      response.status mustBe Status.UNAUTHORIZED
    }

    "Return FORBIDDEN (403) when user has InsufficientEnrolments" in new Setup {

      stubUnauthorised("InsufficientEnrolments")

      val response: WSResponse = client.get().futureValue

      response.status mustBe Status.FORBIDDEN
    }
  }

  s"PUT /company-registration/corporation-tax-registration/$registrationId/language" must {

    "Return NO_CONTENT (204) and update the Mongo document held" in new Setup {

      stubAuthorise(200, "internalId" -> internalId)
      repository.insert(ctReg)

      await(repository.findOneBySelector(repository.regIDSelector(registrationId))).map(_.language) mustBe Some(LangConstants.english)

      val response: WSResponse = client.put(Json.toJson(Language(LangConstants.welsh))).futureValue

      response.status mustBe Status.NO_CONTENT

      await(repository.findOneBySelector(repository.regIDSelector(registrationId))).map(_.language) mustBe Some(LangConstants.welsh)
    }

    "Return NOT_FOUND (404) when no document exists" in new Setup {

      stubAuthorise(200, "internalId" -> internalId)

      val response: WSResponse = client.put(Json.toJson(Language(LangConstants.welsh))).futureValue

      response.status mustBe Status.NOT_FOUND
    }

    "Return UNAUTHORISED (401) when user is not signed in" in new Setup {

      stubUnauthorised("BearerTokenExpired")

      val response: WSResponse = client.put(Json.toJson(Language(LangConstants.welsh))).futureValue

      response.status mustBe Status.UNAUTHORIZED
    }

    "Return FORBIDDEN (403) when user has InsufficientEnrolments" in new Setup {

      stubUnauthorised("InsufficientEnrolments")

      val response: WSResponse = client.put(Json.toJson(Language(LangConstants.welsh))).futureValue

      response.status mustBe Status.FORBIDDEN
    }
  }
}
