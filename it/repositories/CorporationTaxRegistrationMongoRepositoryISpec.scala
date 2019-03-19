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

import auth.CryptoSCRS
import fixtures.CorporationTaxRegistrationFixture.ctRegistrationJson
import itutil.IntegrationSpecBase
import models.RegistrationStatus._
import models._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json, OWrites}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONString}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CorporationTaxRegistrationMongoRepositoryISpec
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

    implicit val jsObjWts: OWrites[JsObject] = OWrites(identity)

    def insert(reg: CorporationTaxRegistration) = {
      val currentCount = count
      await(repository.insert(reg))
      count shouldBe currentCount + 1
    }
    def insertRaw(reg: JsObject) = await(repository.collection.insert(reg))
    def count = await(repository.count)
    def retrieve(regId: String) = await(repository.retrieveCorporationTaxRegistration(regId))
  }

  class SetupWithIndexes(indexList: List[Index]) {
    val rmComp = fakeApplication.injector.instanceOf[ReactiveMongoComponent]
    val crypto = fakeApplication.injector.instanceOf[CryptoSCRS]
    val repository = new CorporationTaxRegistrationMongoRepository(
      rmComp,crypto){
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
     Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
    )),
    tradingDetails = Some(TradingDetails("true")),
    confirmationReferences = Some(ConfirmationReferences(acknowledgementReference = ackRef, "txId", None, None)),
    acknowledgementReferences = Some(AcknowledgementReferences(Option("ctutr").filter(_ => ctutr), "timestamp", status)),
    createdTime = DateTime.parse("2017-09-04T14:49:48.261"),
    lastSignedIn = lastSignedIn
  )

  val registrationId = "testRegId"

  def newCTDoc = CorporationTaxRegistration(
    internalId = "testID",
    registrationID = registrationId,
    formCreationTimestamp = "testDateTime",
    language = "en"
  )

  val testRoAddress = CHROAddress("premises", "address line 1", None, "uk", "local", None, None, None)

  "retrieveCorporationTaxRegistration" should {
    "retrieve a registration with an invalid phone number" in new Setup {
      val registrationId = "testRegId"

      val corporationTaxRegistration = CorporationTaxRegistration(
        internalId = "testID",
        registrationID = registrationId,
        formCreationTimestamp = "testDateTime",
        language = "en",
        contactDetails = Some(ContactDetails(
          phone = Some("12345"),
          mobile = Some("1234567890"),
          email = None))
      )

      await(setupCollection(repository, corporationTaxRegistration))
      val response = repository.retrieveCorporationTaxRegistration(registrationId)
      await(response).flatMap(_.contactDetails) shouldBe corporationTaxRegistration.contactDetails
    }
  }

  "getExistingRegistration" should {
    "throw an exception if no document is found" in new Setup {
      intercept[MissingCTDocument](await(repository.getExistingRegistration(registrationId)))
    }

    "retrieve a document if exists" in new Setup {
      val corporationTaxRegistration = CorporationTaxRegistration(
        internalId = "testID",
        registrationID = registrationId,
        formCreationTimestamp = "testDateTime",
        language = "en",
        contactDetails = Some(ContactDetails(
          phone = Some("12345"),
          mobile = Some("1234567890"),
          email = None))
      )

      val now = DateTime.now()
      insert(corporationTaxRegistration)
      val response = await(repository.getExistingRegistration(registrationId))
      response.copy(createdTime = now, lastSignedIn = now) shouldBe corporationTaxRegistration.copy(createdTime = now, lastSignedIn = now)
    }
  }

  "updateAccountingDetails" should {
    "return some updated company details if document exists" in new Setup {

      val accountingDetails: AccountingDetails  = AccountingDetails("some-status", Some("some-date"))

      insert(newCTDoc)
      val response = await(repository.updateAccountingDetails(registrationId, accountingDetails))
      response shouldBe Some(accountingDetails)
    }

    "return None if no document exists" in new Setup {
      val accountingDetails: AccountingDetails  = AccountingDetails("some-status", Some("some-date"))

      val response = await(repository.updateAccountingDetails(registrationId, accountingDetails))
      response shouldBe None
    }
  }

  "retrieveAccountingDetails" should {
    "return some company details if document exists" in new Setup {
      val accountingDetails: AccountingDetails  = AccountingDetails("some-status", Some("some-date"))

      val corporationTaxRegistrationModel = newCTDoc.copy(
        accountingDetails = Some(accountingDetails)
      )

      insert(corporationTaxRegistrationModel)
      val response = await(repository.retrieveAccountingDetails(registrationId))
      response shouldBe Some(accountingDetails)
    }

    "return None if no accounting details are present in the ct doc" in new Setup {
      insert(newCTDoc)
      val response = await(repository.retrieveAccountingDetails(registrationId))
      response shouldBe None
    }

    "return None if no document exists" in new Setup {
      val response = await(repository.retrieveAccountingDetails(registrationId))
      response shouldBe None
    }
  }

  "updateCompanyDetails" should {
    "return some updated company details if document exists" in new Setup {
      val companyDetails: CompanyDetails  = CompanyDetails("company-name", testRoAddress, PPOB("RO", None), "jurisdiction")

      insert(newCTDoc)
      val response = await(repository.updateCompanyDetails(registrationId, companyDetails))
      response shouldBe Some(companyDetails)
    }

    "return None if no document exists" in new Setup {
      val companyDetails: CompanyDetails  = CompanyDetails("company-name", testRoAddress, PPOB("RO", None), "jurisdiction")

      val response = await(repository.updateCompanyDetails(registrationId, companyDetails))
      response shouldBe None
    }
  }

  "retrieveCompanyDetails" should {
    "return some company details if document exists" in new Setup {
      val registrationId = "testRegId"

      val companyDetails: CompanyDetails  = CompanyDetails("company-name", testRoAddress, PPOB("RO", None), "jurisdiction")

      val corporationTaxRegistrationModel = newCTDoc.copy(
        companyDetails = Some(companyDetails)
      )

      insert(corporationTaxRegistrationModel)
      val response = await(repository.retrieveCompanyDetails(registrationId))
      response shouldBe Some(companyDetails)
    }

    "return None if no company details are present in the ct doc" in new Setup {
      insert(newCTDoc)
      val response = await(repository.retrieveCompanyDetails(registrationId))
      response shouldBe None
    }

    "return None if no document exists" in new Setup {
      val response = await(repository.retrieveCompanyDetails(registrationId))
      response shouldBe None
    }
  }

  "updateTradingDetails" should {
    "return some updated trading details if document exists" in new Setup {
      val tradingDetails: TradingDetails  = TradingDetails("yes")

      insert(newCTDoc)
      val response = await(repository.updateTradingDetails(registrationId, tradingDetails))
      response shouldBe Some(tradingDetails)
    }

    "return override trading details if document already has trading details" in new Setup {
      val tradingDetails: TradingDetails  = TradingDetails("yes")
      val newTradingDetails: TradingDetails  = TradingDetails()

      insert(newCTDoc.copy(tradingDetails = Some(tradingDetails)))
      val response = await(repository.updateTradingDetails(registrationId, newTradingDetails))
      response shouldBe Some(newTradingDetails)
    }

    "return None if no document exists" in new Setup {
      val tradingDetails: TradingDetails  = TradingDetails("yes")

      val response = await(repository.updateTradingDetails(registrationId, tradingDetails))
      response shouldBe None
    }
  }

  "retrieveTradingDetails" should {
    "return some trading details if document exists" in new Setup {
      val registrationId = "testRegId"

      val tradingDetails: TradingDetails  = TradingDetails("yes")

      val corporationTaxRegistrationModel = newCTDoc.copy(
        tradingDetails = Some(tradingDetails)
      )

      insert(corporationTaxRegistrationModel)
      val response = await(repository.retrieveTradingDetails(registrationId))
      response shouldBe Some(tradingDetails)
    }

    "return None if no trading details are present in the ct doc" in new Setup {
      insert(newCTDoc)
      await(repository.retrieveTradingDetails(registrationId)) shouldBe None
    }

    "return None if no document exists" in new Setup {
      await(repository.retrieveTradingDetails(registrationId)) shouldBe None
    }
  }

  "updateContactDetails" should {
    "return some updated contact details if document exists" in new Setup {
      val testContactDetails: ContactDetails = ContactDetails(
        phone = None,
        email = None,
        mobile = None
      )

      insert(newCTDoc)
      await(repository.updateContactDetails(registrationId, testContactDetails)) shouldBe Some(testContactDetails)
    }

    "return override contact details if document already has contact details" in new Setup {
      val contactDetails: ContactDetails = ContactDetails(
        phone = None,
        email = None,
        mobile = None
      )
      val newContactDetails: ContactDetails  = contactDetails.copy(phone = Some("12333334234234"))

      insert(newCTDoc.copy(contactDetails = Some(contactDetails)))
      await(repository.updateContactDetails(registrationId, newContactDetails)) shouldBe Some(newContactDetails)
    }

    "return None if no document exists" in new Setup {
      val contactDetails: ContactDetails = ContactDetails(
        phone = None,
        email = None,
        mobile = None
      )

      await(repository.updateContactDetails(registrationId, contactDetails)) shouldBe None
    }
  }

  "retrieveContactDetails" must {
    "return none when there is no document" in new Setup {
      await(repository.retrieveContactDetails(registrationId)) shouldBe None
    }
    "return none when there are no contact details" in new Setup {
      insert(newCTDoc)
      await(repository.retrieveContactDetails(registrationId)) shouldBe None
    }
    "return some when there are contact details" in new Setup {
      val testContactDetails: ContactDetails = ContactDetails(
        phone = Some("123456"),
        email = None,
        mobile = None
      )
      insert(newCTDoc.copy(contactDetails = Some(testContactDetails)))
      await(repository.retrieveContactDetails(registrationId)) shouldBe Some(testContactDetails)
    }
  }

  "updateConfirmationReferences" should {
    "return some confirmation references if document exists" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      insert(newCTDoc)
      await(repository.updateConfirmationReferences(registrationId, confirmationReferences)) shouldBe Some(confirmationReferences)
    }

    "return override confirmation references if document already has confirmation references" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )
      val newConfirmationReferences: ConfirmationReferences  = confirmationReferences.copy(paymentReference = Some("Some-pay-ref"), paymentAmount = Some("12333334234234"))

      insert(newCTDoc.copy(confirmationReferences = Some(confirmationReferences)))
      await(repository.updateConfirmationReferences(registrationId, newConfirmationReferences)) shouldBe Some(newConfirmationReferences)
    }

    "return None if no document exists" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      await(repository.updateConfirmationReferences(registrationId, confirmationReferences)) shouldBe None
    }
  }

  "retrieveConfirmationReferences" must {
    "return none when there is no document" in new Setup {
      await(repository.retrieveConfirmationReferences(registrationId)) shouldBe None
    }
    "return none when there are no confirmation references" in new Setup {
      insert(newCTDoc)
      await(repository.retrieveConfirmationReferences(registrationId)) shouldBe None
    }
    "return some when there are confirmation references" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      insert(newCTDoc.copy(confirmationReferences = Some(confirmationReferences)))
      await(repository.retrieveConfirmationReferences(registrationId)) shouldBe Some(confirmationReferences)
    }
  }

  "updateConfirmationReferencesAndStatus" should {
    "return some confirmation references if document exists" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      insert(newCTDoc)
      await(repository.updateConfirmationReferencesAndUpdateStatus(registrationId, confirmationReferences, "passed")) shouldBe Some(confirmationReferences)
      retrieve(registrationId).get.status shouldBe "passed"
    }

    "return override confirmation references if document already has confirmation references and status" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )
      val newConfirmationReferences: ConfirmationReferences  = confirmationReferences.copy(paymentReference = Some("Some-pay-ref"), paymentAmount = Some("12333334234234"))

      insert(newCTDoc.copy(confirmationReferences = Some(confirmationReferences), status = "passed"))
      await(repository.updateConfirmationReferencesAndUpdateStatus(registrationId, newConfirmationReferences, "unpassed")) shouldBe Some(newConfirmationReferences)
      retrieve(registrationId).get.status shouldBe "unpassed"
    }

    "return None if no document exists" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      await(repository.updateConfirmationReferencesAndUpdateStatus(registrationId, confirmationReferences, "passed")) shouldBe None
    }
  }

  "updateCompanyEndDate" should {
    "return some Accounting Details prep if document exists" in new Setup {
      val accountingPrepDetails: AccountPrepDetails = AccountPrepDetails(
        status = "end",
        endDate = None
      )

      insert(newCTDoc)
      await(repository.updateCompanyEndDate(registrationId, accountingPrepDetails)) shouldBe Some(accountingPrepDetails)
    }

    "return override accounting Details prep if document already has Accounting Details prep" in new Setup {
      val accountingPrepDetails: AccountPrepDetails = AccountPrepDetails(
        status = "end",
        endDate = None
      )
      val newAccountingPrepDetails: AccountPrepDetails  = accountingPrepDetails.copy(endDate = Some(DateTime.now()))

      insert(newCTDoc.copy(accountsPreparation = Some(accountingPrepDetails)))
      await(repository.updateCompanyEndDate(registrationId, newAccountingPrepDetails)) shouldBe Some(newAccountingPrepDetails)
    }

    "return None if no document exists" in new Setup {
      val accountingPrepDetails: AccountPrepDetails = AccountPrepDetails(
        status = "end",
        endDate = None
      )

      await(repository.updateCompanyEndDate(registrationId, accountingPrepDetails)) shouldBe None
    }
  }

  "updateEmail" should {
    "return some email if document exists" in new Setup {
      val email: Email = Email(
        address = "end@end.end",
        emailType = "type",
        linkSent = false,
        verified = false,
        returnLinkEmailSent = false
      )

      insert(newCTDoc)
      await(repository.updateEmail(registrationId, email)) shouldBe Some(email)
    }

    "return override email if document already has email" in new Setup {
      val email: Email = Email(
        address = "end@end.end",
        emailType = "type",
        linkSent = false,
        verified = false,
        returnLinkEmailSent = false
      )
      val newEmail: Email  = email.copy(linkSent = true, returnLinkEmailSent = true)

      insert(newCTDoc.copy(verifiedEmail = Some(email)))
      await(repository.updateEmail(registrationId, newEmail)) shouldBe Some(newEmail)
    }

    "return None if no document exists" in new Setup {
      val email: Email = Email(
        address = "end@end.end",
        emailType = "type",
        linkSent = false,
        verified = false,
        returnLinkEmailSent = false
      )

      await(repository.updateEmail(registrationId, email)) shouldBe None
    }
  }

  "retrieveEmail" must {
    "return none when there is no document" in new Setup {
      await(repository.retrieveEmail(registrationId)) shouldBe None
    }
    "return none when there is no email block" in new Setup {
      insert(newCTDoc)
      await(repository.retrieveEmail(registrationId)) shouldBe None
    }

    "return some when there is an email block" in new Setup {
      val email: Email = Email(
        address = "some@address.com",
        emailType = "type",
        linkSent = false,
        verified = false,
        returnLinkEmailSent = false
      )

      insert(newCTDoc.copy(verifiedEmail = Some(email)))
      await(repository.retrieveEmail(registrationId)) shouldBe Some(email)
    }
  }

  "updateRegistrationProgress" should {
    "return some registrationPrgoress if document exists" in new Setup {
      val registrationPrgoress: String = "progress"
      insert(newCTDoc)
      await(repository.updateRegistrationProgress(registrationId, registrationPrgoress)) shouldBe Some(registrationPrgoress)
    }

    "return override registration progress if document already has registration progress" in new Setup {
      val registrationProgress: String = "progress"
      val newRegistrationProgress: String = "stopped"

      insert(newCTDoc.copy(registrationProgress = Some(registrationProgress)))
      await(repository.updateRegistrationProgress(registrationId, newRegistrationProgress)) shouldBe Some(newRegistrationProgress)
    }

    "return None if no document exists" in new Setup {
      val registrationProgress: String = "progress"

      await(repository.updateRegistrationProgress(registrationId, registrationProgress)) shouldBe None
    }
  }
  "removeUnnecessaryRegistrationInformation" should {
    "clear all un-needed data" when {
      "mongo statement executes with no errors" in new Setup {
        val registrationId = "testRegId"

        val corporationTaxRegistrationModel = CorporationTaxRegistration(
          internalId = "testID",
          registrationID = registrationId,
          formCreationTimestamp = "testDateTime",
          language = "en"
        )


        await(setupCollection(repository, corporationTaxRegistrationModel))
        await(repository.removeUnnecessaryRegistrationInformation(registrationId)) shouldBe true

        val corporationTaxRegistration: CorporationTaxRegistration = await(repository.retrieveCorporationTaxRegistration(registrationId)).get
        corporationTaxRegistration.verifiedEmail.isEmpty shouldBe true
        corporationTaxRegistration.companyDetails.isEmpty shouldBe true
        corporationTaxRegistration.accountingDetails.isEmpty shouldBe true
        corporationTaxRegistration.registrationID shouldBe corporationTaxRegistrationModel.registrationID
        corporationTaxRegistration.status shouldBe corporationTaxRegistrationModel.status
        corporationTaxRegistration.lastSignedIn shouldBe corporationTaxRegistrationModel.lastSignedIn
      }

      "mongo statement execeutes with the document having missing fields" in new Setup {
        val registrationId = "testRegId"

        val corporationTaxRegistrationModel = CorporationTaxRegistration(
          internalId = "testID",
          registrationID = registrationId,
          formCreationTimestamp = "testDateTime",
          language = "en",
          companyDetails = None
        )

        await(setupCollection(repository, corporationTaxRegistrationModel))
        await(repository.removeUnnecessaryRegistrationInformation(registrationId)) shouldBe true

        val corporationTaxRegistration: CorporationTaxRegistration = await(repository.retrieveCorporationTaxRegistration(registrationId)).get
        corporationTaxRegistration.verifiedEmail.isEmpty shouldBe true
        corporationTaxRegistration.companyDetails.isEmpty shouldBe true
        corporationTaxRegistration.accountingDetails.isEmpty shouldBe true
        corporationTaxRegistration.registrationID shouldBe corporationTaxRegistrationModel.registrationID
        corporationTaxRegistration.status shouldBe corporationTaxRegistrationModel.status
        corporationTaxRegistration.lastSignedIn shouldBe corporationTaxRegistrationModel.lastSignedIn
      }
    }
      "should not update document" when {
        "when the document does not exist" in new Setup {
          val registrationId = "testRegId"
          val incorrectRegistrationId = "notTheTestRegId"

          val corporationTaxRegistrationModel = CorporationTaxRegistration(
            internalId = "testID",
            registrationID = registrationId,
            formCreationTimestamp = "testDateTime",
            language = "en",
            companyDetails = None
          )

          await(setupCollection(repository, corporationTaxRegistrationModel))
          await(repository.removeUnnecessaryRegistrationInformation(incorrectRegistrationId)) shouldBe true
        }
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

      insert(corporationTaxRegistration)

      val response = repository.updateSubmissionStatus(registrationId, status)
      await(response) shouldBe "held"
    }
    "return an exception when document is missing because mapping on result will not contain status" in new Setup {
      intercept[Exception](await(repository.updateSubmissionStatus(registrationId,"foo")))
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
      contactDetails = Some(ContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("true"))
    )

    "remove companyDetails, contactDetails and tradingDetails objects from the collection" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration))

      val response = repository.removeTaxRegistrationInformation(registrationId)
      await(response) shouldBe true
    }
    "remove just company details when contact details and trading details are already empty" in new Setup {
      await(setupCollection(repository, corporationTaxRegistration.copy(tradingDetails = None, contactDetails = None)))

      val response = repository.removeTaxRegistrationInformation(registrationId)
      await(response) shouldBe true
    }
    "throw a MissingCTDocument exception when document does not exist" in new Setup {
      intercept[MissingCTDocument](await(repository.removeTaxRegistrationInformation("foo")))
    }
  }
  "getInternalId" should {
    "return None when no CT Doc exists" in new Setup {
      intercept[MissingCTDocument](await(repository.getInternalId("fooBarWizz")))
    }
    "return Some(regId, InternalId) when ct doc exists" in new Setup {
      insert(newCTDoc)
      await(repository.getInternalId(registrationId)) shouldBe (newCTDoc.registrationID, newCTDoc.internalId)
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
      contactDetails = Some(ContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
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

    "experience an missing ct doc exception if not document exists" in new Setup {
      intercept[MissingCTDocument](await(repository.removeTaxRegistrationById(regId)))
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
      contactDetails = Some(ContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
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
     Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
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
      contactDetails = Some(ContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
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
      contactDetails = Some(ContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
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
      contactDetails = Some(ContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
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
      contactDetails = Some(ContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
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
      contactDetails = Some(ContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
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

  "fetchIndexes" ignore {

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

    val registration90DaysOldLocked: CorporationTaxRegistration = corporationTaxRegistration(
      registrationStatus = "locked",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(90)
    )

    val registration90DaysOldHeld: CorporationTaxRegistration = corporationTaxRegistration(
      registrationStatus = "held",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(90)
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

    val registration90DaysOldHeldWithPaymentRef: CorporationTaxRegistration = corporationTaxRegistration(
      status = "held",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(90)
    ).copy(confirmationReferences = Some(ConfirmationReferences(transactionId = "txId", paymentReference = Some("TEST_PAY_REF"), paymentAmount = None)))

    val registration90DaysOldLockedWithPaymentRef: CorporationTaxRegistration = corporationTaxRegistration(
      status = "locked",
      lastSignedIn = DateTime.now(DateTimeZone.UTC).minusDays(90)
    ).copy(confirmationReferences = Some(ConfirmationReferences(transactionId = "txId", paymentReference = Some("TEST_PAY_REF"), paymentAmount = None)))

    "return 0 documents" when {
      "trying to fetch 1 but the database contains no documents" in new Setup {
        await(repository.retrieveStaleDocuments(1, 90)) shouldBe Nil
      }

      "the database contains 0 documents matching the query when trying to fetch 1" in new Setup {
        insert(registration90DaysOldSubmitted)

        await(repository.retrieveStaleDocuments(1, 91)) shouldBe Nil
      }
      "the database contains documents not matching the query - held/locked with payment reference" in new Setup {
        insert(registration90DaysOldHeldWithPaymentRef)
        insert(registration90DaysOldLockedWithPaymentRef)

        await(repository.retrieveStaleDocuments(1, 91)) shouldBe Nil
      }
      "the database contains documents not matching the query - held/locked without payment reference and held timestamp is 90 days old" in new Setup {
        insert(registration90DaysOldHeld.copy(heldTimestamp = Some(DateTime.now(DateTimeZone.UTC).minusDays(90))))
        insert(registration90DaysOldLocked.copy(heldTimestamp = Some(DateTime.now(DateTimeZone.UTC).minusDays(90))))

        await(repository.retrieveStaleDocuments(1, 91)) shouldBe Nil
      }
    }

    "return the oldest document" when {
      "the database contains 1 document matching the query" in new Setup {
        insert(registration91DaysOldDraft)

        await(repository.retrieveStaleDocuments(1, 90)).head.lastSignedIn.getChronology shouldBe List(registration90DaysOldDraft).head.lastSignedIn.getChronology
      }

      "the database contains multiple documents matching the query but the batch size was set to 1" in new Setup {
        insert(registration90DaysOldDraft)
        insert(registration91DaysOldDraft)

        await(repository.retrieveStaleDocuments(1, 90)) shouldBe List(registration91DaysOldDraft)
      }
    }

    "return multiple documents in order of document age" when {
      "the database contains multiple documents matching the query" in new Setup {
        insert(registration91DaysOldDraft)
        insert(registration91DaysOldLocked)

        await(repository.retrieveStaleDocuments(2, 90)).toSet shouldBe Set(registration91DaysOldLocked, registration91DaysOldDraft)
      }

      "the database contains multiple documents with the same time matching the query" in new Setup {
        insert(registration91DaysOldDraft)
        insert(registration91DaysOldLocked)

        await(repository.retrieveStaleDocuments(2, 90)) should contain theSameElementsAs List(registration91DaysOldDraft, registration91DaysOldLocked)
      }

      "1 of the documents match the query and 3 do not" in new Setup {
        count shouldBe 0
        insert(registration90DaysOldDraft)
        insert(registration91DaysOldLocked)
        insert(registration30DaysOldDraft)
        insert(registration90DaysOldSubmitted)

        val res = await(repository.retrieveStaleDocuments(5, 90))
        res.map(_.status) shouldBe List("draft")
        res.size shouldBe 1
        res shouldBe List(registration91DaysOldLocked)
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
        insert(registration91DaysOldDraft)

        await(repository.retrieveStaleDocuments(2, 90)) shouldBe List(registration91DaysOldDraft)
      }
    }
  }
}