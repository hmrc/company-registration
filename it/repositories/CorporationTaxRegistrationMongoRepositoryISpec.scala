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

import fixtures.CorporationTaxRegistrationFixture.ctRegistrationJson
import models.RegistrationStatus._
import models._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.libs.json.{JsObject, Json, OWrites}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONString}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CorporationTaxRegistrationMongoRepositoryISpec
  extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach
    with ScalaFutures with Eventually with WithFakeApplication {

  class Setup {
    val repository = new CorporationTaxRegistrationMongoRepository(mongo)
    await(repository.drop)
    await(repository.ensureIndexes)

    implicit val jsObjWts: OWrites[JsObject] = OWrites(identity)

    def insert(reg: CorporationTaxRegistration) = await(repository.insert(reg))
    def insertRaw(reg: JsObject) = await(repository.collection.insert(reg))
    def count = await(repository.count)
    def retrieve(regId: String) = await(repository.retrieveCorporationTaxRegistration(regId))
  }

  class SetupWithIndexes(indexList: List[Index]) {
    val repository = new CorporationTaxRegistrationMongoRepository(mongo){
      override def indexes = indexList
    }
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): Future[WriteResult] = {
    repo.insert(ctRegistration)
  }

  val ackRef = "test-ack-ref"

  def corporationTaxRegistration(status: String = "draft",
                                 ctutr: Boolean = true,
                                 lastSignedIn: DateTime = DateTime.now(DateTimeZone.UTC),
                                 registrationStatus: String = RegistrationStatus.DRAFT,
                                 regId: String = UUID.randomUUID().toString) = CorporationTaxRegistration(
    status = status,
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
    confirmationReferences = Some(ConfirmationReferences(acknowledgementReference = ackRef, "txId", None, None)),
    acknowledgementReferences = Some(AcknowledgementReferences(Option("ctutr").filter(_ => ctutr), "timestamp", status)),
    createdTime = DateTime.parse("2017-09-04T14:49:48.261"),
    lastSignedIn = lastSignedIn
  )

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
      lastSignedIn = DateTime.now(DateTimeZone.UTC)
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
      lastSignedIn = DateTime.now(DateTimeZone.UTC)
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
        lastSignedIn = dateTime.withZone(DateTimeZone.UTC),
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
      lastSignedIn = dateTime.withZone(DateTimeZone.UTC)
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

  "retrieveSessionIdentifiers" should {
    val regId = "12345"
    val (defaultSID, defaultCID) = ("oldSessID", "oldCredId")

    val timestamp = DateTime.now()
    def corporationTaxRegistration(regId: String, alreadyHasSessionIds: Boolean = false) = CorporationTaxRegistration(
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
      createdTime = timestamp,
      lastSignedIn = timestamp,
      sessionIdentifiers = if(alreadyHasSessionIds) Some(SessionIds(defaultSID, defaultCID)) else None
    )

    "retrieve session identifiers" when {
      "a document exists and has identifiers" in new Setup {
        await(setupCollection(repository, corporationTaxRegistration(regId, alreadyHasSessionIds = true)))

        await(repository.retrieveSessionIdentifiers(regId)) shouldBe Some(SessionIds(defaultSID, defaultCID))
      }

    }
    "return a None" when {
      "a document exists and does not have identifiers" in new Setup {
        await(setupCollection(repository, corporationTaxRegistration(regId)))

        await(repository.retrieveSessionIdentifiers(regId)) shouldBe None
      }
      "there is no Document" in new Setup {
        await(repository.retrieveSessionIdentifiers(regId)) shouldBe None
      }
    }
  }

  "storeSessionIdentifiers" should {
    val regId = "132"
    val sessionId = "sessionId-12345"
    val credId = "authorisedId"
    val (defaultSID, defaultCID) = ("oldSessID", "oldCredId")

    val timestamp = DateTime.now()
    def corporationTaxRegistration(regId: String, alreadyHasSessionIds: Boolean = false) = CorporationTaxRegistration(
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
      createdTime = timestamp,
      lastSignedIn = timestamp,
      sessionIdentifiers = if(alreadyHasSessionIds) Some(SessionIds(defaultSID, defaultCID)) else None
    )

    "successfully write encrpyted identifiers to mongo" when {

      "a document exists" in new Setup {
        await(setupCollection(repository, corporationTaxRegistration(regId)))
        await(repository.retrieveCorporationTaxRegistration(regId)) map {
          doc => doc.sessionIdentifiers
        } shouldBe Some(None)

        await(repository.storeSessionIdentifiers(regId, sessionId, credId))

        await(repository.retrieveCorporationTaxRegistration(regId)) map {
          doc => doc.sessionIdentifiers
        } shouldBe Some(Some(SessionIds(sessionId, credId)))
      }
      "a document exists and already has old session Ids" in new Setup {

        await(setupCollection(repository, corporationTaxRegistration(regId, alreadyHasSessionIds = true)))
        await(repository.retrieveCorporationTaxRegistration(regId)) map {
          doc => doc.sessionIdentifiers
        } shouldBe Some(Some(SessionIds(defaultSID, defaultCID)))


        await(repository.storeSessionIdentifiers(regId, sessionId, credId))

        await(repository.retrieveCorporationTaxRegistration(regId)) map {
          doc => doc.sessionIdentifiers
        } shouldBe Some(Some(SessionIds(sessionId, credId)))
      }
    }

    "unsuccessful" when {
      "no document exists" in new Setup {
        await(repository.storeSessionIdentifiers(regId, sessionId, credId)) shouldBe false
      }
    }
  }

  "transactionIdSelector" should {
    "return a mapping between a documents transaction id and the value provided" in new Setup {
      val transactionId = "fakeTransId"
      val mapping = BSONDocument("confirmationReferences.transaction-id" -> BSONString(transactionId))
      repository.transactionIdSelector(transactionId) shouldBe mapping
    }
  }

  "updateTransactionId" should {
    "update a document with the new transaction id" when {
      "a document is present in the database" in new Setup {
        val regId = "registrationId"

        def corporationTaxRegistration(transId: String) = CorporationTaxRegistration(
          internalId = "testID",
          registrationID = regId,
          formCreationTimestamp = "testDateTime",
          language = "en",
          confirmationReferences = Some(ConfirmationReferences("ackRef", transId, None, None))
        )

        val updateFrom = "updateFrom"
        val updateTo = "updateTo"

        await(repository.insert(corporationTaxRegistration(updateFrom)))
        await(repository.updateTransactionId(updateFrom, updateTo)) shouldBe updateTo
        await(repository.retrieveCorporationTaxRegistration(regId)) flatMap {
          doc => doc.confirmationReferences
        } shouldBe Some(ConfirmationReferences("ackRef", updateTo, None, None))
      }
    }
    "fail to update a document" when {
      "a document is not present in the database" in new Setup {
        val regId = "registrationId"

        val updateFrom = "updateFrom"
        val updateTo = "updateTo"

        intercept[RuntimeException](await(repository.updateTransactionId(updateFrom, updateTo)))
        await(repository.retrieveCorporationTaxRegistration(regId)) flatMap {
          doc => doc.confirmationReferences
        } shouldBe None
      }
    }
  }

  "fetchIndexes" should {

    val indexes = List(
      Index(
        key = Seq("indexKey" -> IndexType.Ascending),
        name = Some("indexName"),
        unique = true,
        sparse = false,
        version = Some(1)
      ),
      Index(
        key = Seq("indexKey2" -> IndexType.Ascending),
        name = Some("indexName2"),
        unique = true,
        sparse = false,
        version = Some(1)
      )
    )

    val _idIndex = Index(
      key = Seq("_id" -> IndexType.Ascending),
      name = Some("_id_"),
      unique = false,
      sparse = false,
      version = Some(1)
    )

    val indexesWith_idIndex = _idIndex :: indexes

    "return 3 indexes from the collection" when {
      "2 indexes are initially created, excluding the _id index" in new SetupWithIndexes(indexes) {
        val fetchedIndexes = await(repository.fetchIndexes())
        fetchedIndexes should contain theSameElementsAs indexesWith_idIndex
      }

      "3 indexes are created, including the _id index" in new SetupWithIndexes(indexesWith_idIndex) {
        val fetchedIndexes = await(repository.fetchIndexes())
        fetchedIndexes should contain theSameElementsAs indexesWith_idIndex
      }
    }
  }

  "retrieveStaleDocuments" must {

    val registration90DaysOldDraft: CorporationTaxRegistration = corporationTaxRegistration(
      registrationStatus = "draft",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(90)
    )

    val registration91DaysOldLocked: CorporationTaxRegistration = corporationTaxRegistration(
      registrationStatus = "locked",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(91)
    )

    val registration30DaysOldDraft: CorporationTaxRegistration = corporationTaxRegistration(
      registrationStatus = "draft",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(30)
    )

    val registration91DaysOldDraft: CorporationTaxRegistration = corporationTaxRegistration(
      registrationStatus = "draft",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(91)
    )

    val registration90DaysOldSubmitted: CorporationTaxRegistration = corporationTaxRegistration(
      status = "submitted",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(90)
    )

    "return 0 documents" when {
      "trying to fetch 1 but the database contains no documents" in new Setup {
        await(repository.retrieveStaleDocuments(1)) shouldBe Nil
      }

      "the database contains 0 documents matching the query when trying to fetch 1" in new Setup {
        insert(registration90DaysOldSubmitted)

        await(repository.retrieveStaleDocuments(1)) shouldBe Nil
      }
    }

    "return the oldest document" when {
      "the database contains 1 document matching the query" in new Setup {
        insert(registration90DaysOldDraft)

        await(repository.retrieveStaleDocuments(1)).head.lastSignedIn.getChronology shouldBe List(registration90DaysOldDraft).head.lastSignedIn.getChronology
      }

      "the database contains multiple documents matching the query but the batch size was set to 1" in new Setup {
        insert(registration90DaysOldDraft)
        insert(registration91DaysOldDraft)

        await(repository.retrieveStaleDocuments(1)) shouldBe List(registration91DaysOldDraft)
      }
    }

    "return multiple documents in order of document age" when {
      "the database contains multiple documents matching the query" in new Setup {
        insert(registration90DaysOldDraft)
        insert(registration91DaysOldDraft)

        await(repository.retrieveStaleDocuments(2)) shouldBe List(registration91DaysOldDraft, registration90DaysOldDraft)
      }

      "the database contains multiple documents with the same time matching the query" in new Setup {
        insert(registration91DaysOldDraft)
        insert(registration91DaysOldLocked)

        await(repository.retrieveStaleDocuments(2)) should contain theSameElementsAs List(registration91DaysOldDraft, registration91DaysOldLocked)
      }

      "2 of the documents match the query and 2 do not" in new Setup {
        insert(registration90DaysOldDraft)
        insert(registration91DaysOldLocked)
        insert(registration30DaysOldDraft)
        insert(registration90DaysOldSubmitted)

        await(repository.retrieveStaleDocuments(5)) shouldBe List(registration91DaysOldLocked, registration90DaysOldDraft)
      }
    }

    "not fail and continue" when {
      "an invalid registration document is read" in new Setup {
        val incorrectRegistration = ctRegistrationJson(
          regId = registration91DaysOldDraft.registrationID,
          lastSignedIn = registration91DaysOldDraft.lastSignedIn.getMillis,
          malform = Some(Json.obj("registrationID" -> true))
        )
        insertRaw(incorrectRegistration)
        insert(registration90DaysOldDraft)

        await(repository.retrieveStaleDocuments(2)) shouldBe List(registration90DaysOldDraft)
      }
    }

    "return a document if last signed in is not present" in new Setup {
      val registrationId = "regId"
      val registrationNoLastSignedIn = ctRegistrationJson(registrationId) - "lastSignedIn"
      insertRaw(registrationNoLastSignedIn)
      count shouldBe 1
      await(repository.retrieveStaleDocuments(1)).size shouldBe 1
    }
  }
}
