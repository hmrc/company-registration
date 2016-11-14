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

import java.util.UUID

import models.RegistrationStatus._
import models._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.domain.CtUtr
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CorporationTaxRegistrationMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

	class Setup {
		val repository = new CorporationTaxRegistrationMongoRepository()
		await(repository.drop)
		await(repository.ensureIndexes)
	}

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): Future[WriteResult] = {
    repo.insert(ctRegistration)
  }

  "updateSubmissionStatus" should {

    val registrationId = "testRegId"

    val corporationTaxRegistration = CorporationTaxRegistration(
      OID = "testOID",
      registrationID = registrationId,
      formCreationTimestamp = "testDateTime",
      language = "en"
    )

    "return an updated held status" in new Setup {
      val status = "held"

      await(setupCollection(repository, corporationTaxRegistration))

      val response = repository.updateSubmissionStatus(registrationId, status)
      await(response) shouldBe "held"
    }
  }

  "removeTaxRegistrationInformation" should {

    val registrationId = "testRegId"

    val corporationTaxRegistration = CorporationTaxRegistration(
      OID = "testOID",
      registrationID = registrationId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        ROAddress("10", "test street", "test town", "test area", "test county", "XX1 1ZZ", "test country"),
        PPOBAddress("10", "test street", Some("test town"), Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country")),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails(true))
    )

    "remove companyDetails, contactDetails and tradingDetails objects from the collection" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration))

      val response = repository.removeTaxRegistrationInformation(registrationId)
      await(response) shouldBe true
    }
  }

  "updateCTRecordWithAcknowledgments" should {

    val ackRef = "BRCT12345678910"

    val validConfirmationReferences = ConfirmationReferences(
      acknowledgementReference = "BRCT12345678910",
      transactionId = "TX1",
      paymentReference = "PY1",
      paymentAmount = "12.00"
    )

    val validAckRefs = AcknowledgementReferences(
      ctUtr = "CTUTR123456789",
      timestamp = "856412556487",
      status = "success"
    )

    val validHeldCorporationTaxRegistration = CorporationTaxRegistration(
      OID = "9876543210",
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

    "return a WriteResult with no errors" in new Setup {
      val result = await(repository.updateCTRecordWithAcknowledgments(ackRef, validHeldCorporationTaxRegistration))
      result.hasErrors shouldBe false
    }
  }

  "getHeldCTRecord" should {

    val ackRef = "BRCT12345678910"

    val validConfirmationReferences = ConfirmationReferences(ackRef, "TX1", "PY1", "12.00")

    val validHeldCorporationTaxRegistration = CorporationTaxRegistration(
      OID = "9876543210",
      registrationID = "0123456789",
      status = HELD,
      formCreationTimestamp = "2001-12-31T12:00:00Z",
      language = "en",
      confirmationReferences = Some(validConfirmationReferences)
    )

    "return an optional ct record" when {
      "given an ack ref" in new Setup {
        await(setupCollection(repository, validHeldCorporationTaxRegistration))

        val result = await(repository.getHeldCTRecord(ackRef)).get
        result shouldBe validHeldCorporationTaxRegistration
      }
    }
  }

  "Update registration to submitted (with data)" should {

    val ackRef = "BRCT12345678910"

    val heldReg = CorporationTaxRegistration(
      OID = "9876543210",
      registrationID = "0123456789",
      status = HELD,
      formCreationTimestamp = "2001-12-31T12:00:00Z",
      language = "en",
      confirmationReferences = Some(ConfirmationReferences(ackRef, "TX1", "PY1", "12.00")),
      accountingDetails = Some(AccountingDetails(AccountingDetails.WHEN_REGISTERED, None)),
      accountsPreparation = Some(AccountPrepDetails(AccountPrepDetails.HMRC_DEFINED, None))
    )

    "update the CRN and timestamp" in new Setup {
      await(setupCollection(repository, heldReg))

      val crn = "foo1234"
      val submissionTS = "2001-12-31T12:00:00Z"

      val result = await(repository.updateHeldToSubmitted(heldReg.registrationID, crn, submissionTS))

      result shouldBe true

      val someActual = await(repository.retrieveCorporationTaxRegistration(heldReg.registrationID))
      someActual shouldBe defined
      val actual = someActual.get
      actual.status shouldBe RegistrationStatus.SUBMITTED
      actual.crn shouldBe Some(crn)
      actual.submissionTimestamp shouldBe Some(submissionTS)
      actual.accountingDetails shouldBe None
      actual.accountsPreparation shouldBe None
    }

    "fail to update the CRN" in new Setup {

      await(setupCollection(repository, heldReg.copy(registrationID = "ABC")))

      val crn = "foo1234"
      val submissionTS = "2001-12-31T12:00:00Z"

      intercept[MissingCTDocument]{await(repository.updateHeldToSubmitted(heldReg.registrationID, crn, submissionTS))}

    }

  }

  "removeTaxRegistrationById" should {
    val registrationId = UUID.randomUUID.toString

    val corporationTaxRegistration = CorporationTaxRegistration(
      OID = "testOID",
      registrationID = registrationId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        ROAddress("10", "test street", "test town", "test area", "test county", "XX1 1ZZ", "test country"),
        PPOBAddress("10", "test street", Some("test town"), Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country")),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails(true))
    )

    "remove all details under that RegId from the collection" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration))

      def retrieve = await(repository.retrieveCorporationTaxRegistration(registrationId))

      lazy val response = repository.removeTaxRegistrationById(registrationId)

      retrieve shouldBe Some(corporationTaxRegistration)
      await(response) shouldBe true
      retrieve  shouldBe None
    }
  }
}
