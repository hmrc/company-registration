/*
 * Copyright 2022 HM Revenue & Customs
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
import models.CorporationTaxRegistration
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.http.{HeaderNames => GovHeaderNames}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationProgressISpec extends IntegrationSpecBase with LoginStub with MongoIntegrationSpec {

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
      GovHeaderNames.authorisation -> "Bearer123")

  class Setup {
    val repository = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    repository.deleteAll
    await(repository.ensureIndexes)
  }

  "Company registration progress" must {

    val registrationId = UUID.randomUUID().toString
    val internalId = UUID.randomUUID().toString

    val ctReg = CorporationTaxRegistration(
      internalId,
      registrationId,
      "draft",
      "testDateTime",
      "en",
      createdTime = Instant.now().minus(1, ChronoUnit.DAYS),
      lastSignedIn = Instant.now(),
    )

    "Update the CR doc successfully with the progress info" in new Setup {
      stubAuthorise(200, "internalId" -> internalId)

      repository.insert(ctReg)

      val progress = "HO5"
      val response = client(s"/${registrationId}/progress").put(s"""{"registration-progress": "${progress}"}""").futureValue

      response.status mustBe 200

      val doc = await(repository.findOneBySelector(repository.regIDSelector(registrationId)))

      doc mustBe defined
      doc.get.registrationProgress mustBe Some(progress)
    }
  }
}
