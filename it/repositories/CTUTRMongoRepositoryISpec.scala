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
import models.RegistrationStatus._
import models._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONInteger, BSONString}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CTUTRMongoRepositoryISpec
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

    lazy val repository = new CorporationTaxRegistrationMongoRepository(rmc,crypto)
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): Future[WriteResult] = {
    repo.insert(ctRegistration)
  }

  "CT UTR Encryption" should {

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
      language = "en",
      acknowledgementReferences = Some(validAckRefs),
      confirmationReferences = Some(validConfirmationReferences),
      companyDetails = None,
      accountingDetails = None,
      tradingDetails = None,
      contactDetails = None
    )

    "store the plain UTR in encrypted form" in new Setup {
      import reactivemongo.play.json.ImplicitBSONHandlers._

      val ctUtr = validHeldCorporationTaxRegistration.acknowledgementReferences.get.ctUtr
      await(setupCollection(repository, validHeldCorporationTaxRegistration))
      val result = await(repository.updateCTRecordWithAcknowledgments(ackRef, validHeldCorporationTaxRegistration))
      result.writeErrors shouldBe Seq()

      // check the value isn't the UTR when fetched direct from the DB
      val query = BSONDocument("confirmationReferences.acknowledgement-reference" -> BSONString(ackRef))
      val project = BSONDocument("acknowledgementReferences.ct-utr" -> BSONInteger(1), "_id" -> BSONInteger(0))
      val stored: Option[JsObject] = await(repository.collection.find(query, project).one[JsObject])
      stored shouldBe defined
      (stored.get \ "acknowledgementReferences" \ "ct-utr").as[String] shouldNot be(ctUtr)

      // check that it is the UTR when fetched properly
      val fetched: CorporationTaxRegistration = await(repository.retrieveByAckRef(ackRef)).get
      fetched.acknowledgementReferences.map(_.ctUtr) shouldBe Some(ctUtr)
    }
  }
}
