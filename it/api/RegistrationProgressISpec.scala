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
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WS
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.CorporationTaxRegistrationMongoRepository

import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationProgressISpec extends IntegrationSpecBase with LoginStub {

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
    withHeaders("Content-Type"->"application/json")

  class Setup {
    val rmComp = app.injector.instanceOf[ReactiveMongoComponent]
    val crypto = app.injector.instanceOf[CryptoSCRS]
    val repository = new CorporationTaxRegistrationMongoRepository(
      rmComp,crypto)
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  "Company registration progress" should {

    val registrationId = UUID.randomUUID().toString
    val internalId = UUID.randomUUID().toString

    import reactivemongo.play.json._

    val jsonDoc = Json.parse(
      s"""
         |{
         |"internalId":"${internalId}","registrationID":"${registrationId}","status":"draft","formCreationTimestamp":"testDateTime","language":"en",
         |"createdTime":1488304097470,"lastSignedIn":1488304097486
         |}
      """.stripMargin).as[JsObject]

    "Update the CR doc successfully with the progress info" in new Setup {
      stubAuthorise(200, "internalId" -> internalId)

      await(repository.collection.insert(jsonDoc))

      val progress = "HO5"
      val response = client(s"/${registrationId}/progress").put(s"""{"registration-progress": "${progress}"}""").futureValue

      response.status shouldBe 200

      val doc = await( repository.retrieveCorporationTaxRegistration(registrationId) )

      doc shouldBe defined
      doc.get.registrationProgress shouldBe Some(progress)
    }
  }
}
