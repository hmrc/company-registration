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

package repositories

import config.LangConstants
import itutil.{IntegrationSpecBase, MongoIntegrationSpec}
import models.RegistrationStatus._
import models._
import org.mongodb.scala.model.Filters
import org.mongodb.scala.result.InsertOneResult
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global

class CTUTRMongoRepositoryISpec extends IntegrationSpecBase with MongoIntegrationSpec {

  val additionalConfiguration = Map(
    "schedules.missing-incorporation-job.enabled" -> "false",
    "schedules.metrics-job.enabled" -> "false",
    "schedules.remove-stale-documents-job.enabled" -> "false"
  )
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  class Setup {
    lazy val repository = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    repository.deleteAll
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): InsertOneResult =
    repo.insert(ctRegistration)

  "CT UTR Encryption" must {

    val ackRef = "BRCT12345678910"

    val validConfirmationReferences = ConfirmationReferences(
      acknowledgementReference = "BRCT12345678910",
      transactionId = "TX1",
      paymentReference = Some("PY1"),
      paymentAmount = Some("12.00")
    )

    val validAckRefs = AcknowledgementReferences(
      ctUtr = Option("CTUTR123456789"),
      timestamp = "856412556487",
      status = "success"
    )

    val validHeldCorporationTaxRegistration = CorporationTaxRegistration(
      internalId = "9876543210",
      registrationID = "0123456789",
      status = HELD,
      formCreationTimestamp = "2001-12-31T12:00:00Z",
      language = LangConstants.english,
      acknowledgementReferences = Some(validAckRefs),
      confirmationReferences = Some(validConfirmationReferences),
      companyDetails = None,
      accountingDetails = None,
      tradingDetails = None,
      contactDetails = None
    )

    "store the plain UTR in encrypted form" in new Setup {

      val ctUtr = validHeldCorporationTaxRegistration.acknowledgementReferences.get.ctUtr
      setupCollection(repository, validHeldCorporationTaxRegistration)
      val result = await(repository.updateCTRecordWithAcknowledgments(ackRef, validHeldCorporationTaxRegistration))
      result.getMatchedCount mustBe 1

      // check the value isn't the UTR when fetched direct from the DB
      val query = Filters.equal("confirmationReferences.acknowledgement-reference", ackRef)
      val stored: Option[CorporationTaxRegistration] = await(repository.collection.find(query).headOption())
      stored mustBe defined
      stored.get.acknowledgementReferences.get.ctUtr.get mustNot be(ctUtr)

      // check that it is the UTR when fetched properly
      val fetched: CorporationTaxRegistration = await(repository.findOneBySelector(repository.ackRefSelector(ackRef))).get
      fetched.acknowledgementReferences.map(_.ctUtr) mustBe Some(ctUtr)
    }
  }
}
