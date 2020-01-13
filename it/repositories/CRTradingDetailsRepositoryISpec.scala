/*
 * Copyright 2016 HM Revenue & Customs
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

package repositories

import auth.CryptoSCRS
import itutil.IntegrationSpecBase
import models._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CRTradingDetailsRepositoryISpec
  extends IntegrationSpecBase {

  val additionalConfiguration = Map(
    "schedules.missing-incorporation-job.enabled" -> "false",
    "schedules.metrics-job.enabled" -> "false",
    "schedules.remove-stale-documents-job.enabled" -> "false"
  )
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  class Setup {
    val rmc = app.injector.instanceOf[ReactiveMongoComponent]
    val crypto = app.injector.instanceOf[CryptoSCRS]

    val repository = new CorporationTaxRegistrationMongoRepository(rmc,crypto)
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): Future[WriteResult] = {
    repo.insert(ctRegistration)
  }

  "Trading details in the CR registration" should {

    val registrationId = "testRegId"

    def json(tradingDetails: String) = Json.parse(
      s"""
        |{
        |"internalId":"testID","registrationID":"${registrationId}","status":"draft","formCreationTimestamp":"testDateTime","language":"en",
        |"companyDetails":{"companyName":"testCompanyName",
        |  "cHROAddress":{"premises":"Premises","address_line_1":"Line 1","address_line_2":"Line 2","country":"Country","locality":"Locality","po_box":"PO box","postal_code":"Post code","region":"Region"},
        |  "pPOBAddress":{"addressType":"MANUAL","address":{"addressLine1":"10 test street","addressLine2":"test town","addressLine3":"test area","addressLine4":"test county","postCode":"XX1 1ZZ","country":"test country","txid":"txid"}},
        |  "jurisdiction":"testJurisdiction"
        |  },
        |${tradingDetails}
        |"contactDetails":{"contactFirstName":"testFirstName","contactMiddleName":"testMiddleName","contactSurname":"testSurname","contactDaytimeTelephoneNumber":"0123456789","contactMobileNumber":"0123456789","contactEmail":"test@email.co.uk"},
        |"createdTime":1488304097470,"lastSignedIn":1488304097486
        |}
      """.stripMargin).as[JsObject]

    "process with a regular payments string properly" in new Setup {
      import reactivemongo.play.json._

      val tradingDetails = """ "tradingDetails":{"regularPayments":"true"}, """
      await(repository.collection.insert(json(tradingDetails)))

      val response = repository.retrieveCorporationTaxRegistration(registrationId)

      await(response.get.tradingDetails.get.regularPayments) shouldBe "true"
    }

    "process with a regular payments boolean properly" in new Setup {
      import reactivemongo.play.json._

      val tradingDetails = """ "tradingDetails":{"regularPayments":true}, """
      await(repository.collection.insert(json(tradingDetails)))

      val response = repository.retrieveCorporationTaxRegistration(registrationId)

      await(response.get.tradingDetails.get.regularPayments) shouldBe "true"
    }
  }
}
