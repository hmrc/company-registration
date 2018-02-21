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
import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CorporationTaxRegistrationMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  class Setup {
    val repository = new CorporationTaxRegistrationMongoRepository(mongo)
    await(repository.drop)
    await(repository.ensureIndexes)

    def insert(reg: CorporationTaxRegistration) = await(repository.insert(reg))
    def count = await(repository.count)
    def retrieve(regId: String) = await(repository.retrieveCorporationTaxRegistration(regId))
  }

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): Future[WriteResult] = {
    repo.insert(ctRegistration)
  }

  "retrieveCorporationTaxRegistration" should {
    "retrieve a registration with an invalid phone number" in new Setup {
      val registrationId = "testRegId"

      val corporationTaxRegistration = CorporationTaxRegistration(
        internalId = "testID",
        registrationID = registrationId,
        formCreationTimestamp = "testDateTime",
        language = "en",
        contactDetails = Some(ContactDetails(
          firstName = "First",
          middleName = Some("Middle"),
          surname = "Sur",
          phone = Some("12345"),
          mobile = Some("1234567890"),
          email = None))
      )

      await(setupCollection(repository, corporationTaxRegistration))
      val response = repository.retrieveCorporationTaxRegistration(registrationId)
      await(response).flatMap(_.contactDetails) shouldBe corporationTaxRegistration.contactDetails
    }
  }

  "updateSubmissionStatus" should {

    val registrationId = "testRegId"

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
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
      internalId = "testID",
      registrationID = registrationId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("true"))
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

    "return a WriteResult with no errors" in new Setup {
      val result = await(repository.updateCTRecordWithAcknowledgments(ackRef, validHeldCorporationTaxRegistration))
      result.writeErrors shouldBe Seq()
    }
  }

  "retrieveByAckRef" should {

    val ackRef = "BRCT12345678910"

    val validConfirmationReferences = ConfirmationReferences(ackRef, "TX1", Some("PY1"), Some("12.00"))

    val validHeldCorporationTaxRegistration = CorporationTaxRegistration(
      internalId = "9876543210",
      registrationID = "0123456789",
      status = HELD,
      formCreationTimestamp = "2001-12-31T12:00:00Z",
      language = "en",
      confirmationReferences = Some(validConfirmationReferences),
      createdTime = DateTime.now,
      lastSignedIn = DateTime.now
    )

    "return an optional ct record" when {

      "given an ack ref" in new Setup {
        await(setupCollection(repository, validHeldCorporationTaxRegistration))

        val result = await(repository.retrieveByAckRef(ackRef)).get
        result shouldBe validHeldCorporationTaxRegistration
      }
    }
  }

  "Update registration to submitted (with data)" should {

    val ackRef = "BRCT12345678910"

    val heldReg = CorporationTaxRegistration(
      internalId = "9876543210",
      registrationID = "0123456789",
      status = HELD,
      formCreationTimestamp = "2001-12-31T12:00:00Z",
      language = "en",
      confirmationReferences = Some(ConfirmationReferences(ackRef, "TX1", Some("PY1"), Some("12.00"))),
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
    val regId = UUID.randomUUID.toString

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "intId",
      registrationID = regId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("LOOKUP", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), Some("xxx"), "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("true")),
      createdTime = DateTime.now,
      lastSignedIn = DateTime.now
    )

    "remove all details under that RegId from the collection" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration))


      lazy val response = repository.removeTaxRegistrationById(regId)

      retrieve(regId) shouldBe Some(corporationTaxRegistration)
      await(response) shouldBe true
      retrieve(regId)  shouldBe None
    }

    "remove only the data associated with the supplied regId" in new Setup {
      count shouldBe 0
      insert(corporationTaxRegistration)
      insert(corporationTaxRegistration.copy(registrationID = "otherRegId"))
      count shouldBe 2

      val result = await(repository.removeTaxRegistrationById(regId))

      count shouldBe 1
      retrieve(regId) shouldBe None
      retrieve("otherRegId") shouldBe Some(corporationTaxRegistration.copy(registrationID = "otherRegId"))
    }
  }

  "Registration Progress" should {
    val registrationId = UUID.randomUUID.toString

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "intId",
      registrationID = registrationId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      registrationProgress = None
    )

    "be stored in the document correctly" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration))

      def retrieve = await(repository.retrieveCorporationTaxRegistration(registrationId))

      retrieve.get.registrationProgress shouldBe None

      val progress = "foo"
      await(repository.updateRegistrationProgress(registrationId, progress))

      retrieve.get.registrationProgress shouldBe Some(progress)
    }
  }

  "updateRegistrationToHeld" should {

    val regId = "reg-12345"
    val dateTime = DateTime.parse("2017-09-04T14:49:48.261")

    val validConfirmationReferences = ConfirmationReferences(
      acknowledgementReference = "BRCT12345678910",
      transactionId = "TX1",
      paymentReference = Some("PY1"),
      paymentAmount = Some("12.00")
    )

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("true")),
      status = RegistrationStatus.DRAFT,
      createdTime = dateTime,
      lastSignedIn = dateTime
    )

    "update registration status to held, set confirmation refs and remove trading details, contact details and company details" in new Setup {

      await(setupCollection(repository, corporationTaxRegistration))

      val Some(result): Option[CorporationTaxRegistration] = await(repository.updateRegistrationToHeld(regId, validConfirmationReferences))

      val heldTs: Option[DateTime] = result.heldTimestamp

      val Some(expected): Option[CorporationTaxRegistration] = Some(CorporationTaxRegistration(
        internalId = "testID",
        registrationID = regId,
        formCreationTimestamp = "testDateTime",
        language = "en",
        companyDetails = None,
        contactDetails = None,
        tradingDetails = None,
        status = RegistrationStatus.HELD,
        confirmationReferences = Some(validConfirmationReferences),
        createdTime = dateTime,
        lastSignedIn = dateTime,
        heldTimestamp = heldTs
      ))

      result shouldBe expected
    }
  }

  "retrieveLockedRegIds" should {

    val regId = "reg-12345"
    val lockedRegId = "reg-54321"
    val dateTime = DateTime.parse("2017-09-04T14:49:48.261")

    val validConfirmationReferences = ConfirmationReferences(
      acknowledgementReference = "BRCT12345678910",
      transactionId = "TX1",
      paymentReference = Some("PY1"),
      paymentAmount = Some("12.00")
    )

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("true")),
      status = RegistrationStatus.DRAFT,
      createdTime = dateTime,
      lastSignedIn = dateTime
    )

    val lockedCorporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = lockedRegId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("true")),
      status = RegistrationStatus.LOCKED,
      createdTime = dateTime,
      lastSignedIn = dateTime
    )

    "retrieve only regids with the status of LOCKED" in new Setup {

      await(setupCollection(repository, corporationTaxRegistration))
      await(setupCollection(repository, lockedCorporationTaxRegistration))

      val result = await(repository.retrieveLockedRegIDs())

      result shouldBe List(lockedRegId)
    }
  }

  "retrieveStatusAndExistenceOfCTUTRByAckRef" should {

    val ackRef = "BRCT09876543210"

    def corporationTaxRegistration(status: String, ctutr: Boolean) = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = "regId",
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("true")),
      status = RegistrationStatus.ACKNOWLEDGED,
      confirmationReferences = Some(ConfirmationReferences(acknowledgementReference = ackRef, "txId", None, None)),
      acknowledgementReferences = Some(AcknowledgementReferences(Option("ctutr").filter(_ => ctutr), "timestamp", status)),
      createdTime = DateTime.parse("2017-09-04T14:49:48.261"),
      lastSignedIn = DateTime.parse("2017-09-04T14:49:48.261")
    )

    "retrieve nothing when registration is not found" in new Setup {
      val result = await(repository.retrieveStatusAndExistenceOfCTUTR("Non-existent AckRef"))
      result shouldBe None
    }
    "retrieve a CTUTR (as a Boolean) when the ackref status is 04" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration("04", ctutr = true)))

      val result = await(repository.retrieveStatusAndExistenceOfCTUTR(ackRef))
      result shouldBe Option("04" -> true)
    }
    "retrieve no CTUTR (as a Boolean) when the ackref status is 06" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration("06", ctutr = false)))

      val result = await(repository.retrieveStatusAndExistenceOfCTUTR(ackRef))
      result shouldBe Option("06" -> false)
    }
  }

  "updateRegistrationWithAdminCTReference" should {

    val ackRef = "BRCT09876543210"
    val dateTime = DateTime.parse("2017-09-04T14:49:48.261")
    val regId = "regId"
    val confRefs = Some(ConfirmationReferences(acknowledgementReference = ackRef, "txId", None, None))
    val timestamp = DateTime.now().toString

    def corporationTaxRegistration(status: String, ctutr: Boolean) = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName", Some("testMiddleName"), "testSurname", Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("true")),
      status = RegistrationStatus.DRAFT,
      confirmationReferences = confRefs,
      acknowledgementReferences = Some(AcknowledgementReferences(Option("ctutr").filter(_ => ctutr), timestamp, status)),
      createdTime = dateTime,
      lastSignedIn = dateTime
    )

    "update a registration with an admin ct reference" in new Setup {
      val newUtr = "newUtr"
      val newStatus = "04"

      await(setupCollection(repository, corporationTaxRegistration("06", ctutr = false)))

      val Some(result) : Option[CorporationTaxRegistration] = await(repository.updateRegistrationWithAdminCTReference(ackRef, newUtr))

      result shouldBe corporationTaxRegistration("06", ctutr = false)
    }

    "not update a registration that does not exist" in new Setup {
      val newUtr = "newUtr"
      val newStatus = "04"

      val result : Option[CorporationTaxRegistration] = await(repository.updateRegistrationWithAdminCTReference(ackRef, newUtr))
      val expected : Option[CorporationTaxRegistration] = None

      result shouldBe expected
    }
  }
}
