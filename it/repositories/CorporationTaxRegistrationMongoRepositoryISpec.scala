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

import auth.CryptoSCRS
import fixtures.CorporationTaxRegistrationFixture
import fixtures.CorporationTaxRegistrationFixture.ctRegistrationJson
import itutil.ItTestConstants.CorporationTaxRegistration.corpTaxRegModel
import itutil.ItTestConstants.TakeoverDetails.{testTakeoverDetails, testTakeoverDetailsModel}
import itutil.{IntegrationSpecBase, MongoIntegrationSpec}
import models.RegistrationStatus._
import models._
import models.des.BusinessAddress
import models.validation.MongoValidation
import org.joda.time.{DateTime, DateTimeZone}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.result.InsertOneResult
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.test.Helpers._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CorporationTaxRegistrationMongoRepositoryISpec
  extends IntegrationSpecBase with MongoIntegrationSpec {

  val additionalConfiguration = Map(
    "schedules.missing-incorporation-job.enabled" -> "false",
    "schedules.metrics-job.enabled" -> "false",
    "schedules.remove-stale-documents-job.enabled" -> "false"
  )
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  class Setup {
    val repository = app.injector.instanceOf[CorporationTaxRegistrationMongoRepository]
    repository.deleteAll
    await(repository.ensureIndexes)

    implicit val jsObjWts: OWrites[JsObject] = OWrites(identity)

    def insert(reg: CorporationTaxRegistration) = {
      val currentCount = count
      repository.insert(reg)
      count mustBe currentCount + 1
    }

    def count = repository.count

    def retrieve(regId: String) = await(repository.findOneBySelector(repository.regIDSelector(regId)))
  }

  val validGroupsModel = Groups(
    groupRelief = true,
    nameOfCompany = Some(GroupCompanyName("testGroupName", GroupCompanyNameEnum.Other)),
    addressAndType = Some(GroupsAddressAndType(GroupAddressTypeEnum.ALF, BusinessAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A")))),
    groupUTR = Some(GroupUTR(Some("1234567890"))))

  def setupCollection(repo: CorporationTaxRegistrationMongoRepository, ctRegistration: CorporationTaxRegistration): InsertOneResult = {
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

  "retrieveCorporationTaxRegistration" must {
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

      setupCollection(repository, corporationTaxRegistration)
      val response = repository.findOneBySelector(repository.regIDSelector(registrationId))
      await(response).flatMap(_.contactDetails) mustBe corporationTaxRegistration.contactDetails
    }
    "retrieve ctdoc with new groups block" in new Setup {
      val ctDoc = corporationTaxRegistration(regId = "123").copy(groups = Some(validGroupsModel))
      val resOfInsert = await(repository.createCorporationTaxRegistration(ctDoc))
      count mustBe 1
      val response = await(repository.findOneBySelector(repository.regIDSelector(resOfInsert.registrationID)))
      response.get.groups.get mustBe validGroupsModel
    }
  }

  "getExistingRegistration" must {
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
      response.copy(createdTime = now, lastSignedIn = now) mustBe corporationTaxRegistration.copy(createdTime = now, lastSignedIn = now)
    }
  }

  "updateAccountingDetails" must {
    "return some updated company details if document exists" in new Setup {

      val accountingDetails: AccountingDetails = AccountingDetails("some-status", Some("some-date"))

      insert(newCTDoc)
      val response = await(repository.updateAccountingDetails(registrationId, accountingDetails))
      response mustBe Some(accountingDetails)
    }

    "return None if no document exists" in new Setup {
      val accountingDetails: AccountingDetails = AccountingDetails("some-status", Some("some-date"))

      val response = await(repository.updateAccountingDetails(registrationId, accountingDetails))
      response mustBe None
    }
  }

  "retrieveAccountingDetails" must {
    "return some company details if document exists" in new Setup {
      val accountingDetails: AccountingDetails = AccountingDetails("some-status", Some("some-date"))

      val corporationTaxRegistrationModel = newCTDoc.copy(
        accountingDetails = Some(accountingDetails)
      )

      insert(corporationTaxRegistrationModel)
      val response = await(repository.retrieveAccountingDetails(registrationId))
      response mustBe Some(accountingDetails)
    }

    "return None if no accounting details are present in the ct doc" in new Setup {
      insert(newCTDoc)
      val response = await(repository.retrieveAccountingDetails(registrationId))
      response mustBe None
    }

    "return None if no document exists" in new Setup {
      val response = await(repository.retrieveAccountingDetails(registrationId))
      response mustBe None
    }
  }

  "updateCompanyDetails" must {
    "return some updated company details if document exists" in new Setup {
      val companyDetails: CompanyDetails = CompanyDetails("company-name", testRoAddress, PPOB("RO", None), "jurisdiction")

      insert(newCTDoc)
      val response = await(repository.updateCompanyDetails(registrationId, companyDetails))
      response mustBe Some(companyDetails)
    }

    "return None if no document exists" in new Setup {
      val companyDetails: CompanyDetails = CompanyDetails("company-name", testRoAddress, PPOB("RO", None), "jurisdiction")

      val response = await(repository.updateCompanyDetails(registrationId, companyDetails))
      response mustBe None
    }
  }

  "retrieveCompanyDetails" must {
    "return some company details if document exists" in new Setup {
      val registrationId = "testRegId"

      val companyDetails: CompanyDetails = CompanyDetails("company-name", testRoAddress, PPOB("RO", None), "jurisdiction")

      val corporationTaxRegistrationModel = newCTDoc.copy(
        companyDetails = Some(companyDetails)
      )

      insert(corporationTaxRegistrationModel)
      val response = await(repository.retrieveCompanyDetails(registrationId))
      response mustBe Some(companyDetails)
    }

    "return None if no company details are present in the ct doc" in new Setup {
      insert(newCTDoc)
      val response = await(repository.retrieveCompanyDetails(registrationId))
      response mustBe None
    }

    "return None if no document exists" in new Setup {
      val response = await(repository.retrieveCompanyDetails(registrationId))
      response mustBe None
    }
  }

  "updateTradingDetails" must {
    "return some updated trading details if document exists" in new Setup {
      val tradingDetails: TradingDetails = TradingDetails("yes")

      insert(newCTDoc)
      val response = await(repository.updateTradingDetails(registrationId, tradingDetails))
      response mustBe Some(tradingDetails)
    }

    "return override trading details if document already has trading details" in new Setup {
      val tradingDetails: TradingDetails = TradingDetails("yes")
      val newTradingDetails: TradingDetails = TradingDetails()

      insert(newCTDoc.copy(tradingDetails = Some(tradingDetails)))
      val response = await(repository.updateTradingDetails(registrationId, newTradingDetails))
      response mustBe Some(newTradingDetails)
    }

    "return None if no document exists" in new Setup {
      val tradingDetails: TradingDetails = TradingDetails("yes")

      val response = await(repository.updateTradingDetails(registrationId, tradingDetails))
      response mustBe None
    }
  }

  "retrieveTradingDetails" must {
    "return some trading details if document exists" in new Setup {
      val registrationId = "testRegId"

      val tradingDetails: TradingDetails = TradingDetails("yes")

      val corporationTaxRegistrationModel = newCTDoc.copy(
        tradingDetails = Some(tradingDetails)
      )

      insert(corporationTaxRegistrationModel)
      val response = await(repository.retrieveTradingDetails(registrationId))
      response mustBe Some(tradingDetails)
    }

    "return None if no trading details are present in the ct doc" in new Setup {
      insert(newCTDoc)
      await(repository.retrieveTradingDetails(registrationId)) mustBe None
    }

    "return None if no document exists" in new Setup {
      await(repository.retrieveTradingDetails(registrationId)) mustBe None
    }
  }

  "updateContactDetails" must {
    "return some updated contact details if document exists" in new Setup {
      val testContactDetails: ContactDetails = ContactDetails(
        phone = None,
        email = None,
        mobile = None
      )

      insert(newCTDoc)
      await(repository.updateContactDetails(registrationId, testContactDetails)) mustBe Some(testContactDetails)
    }

    "return override contact details if document already has contact details" in new Setup {
      val contactDetails: ContactDetails = ContactDetails(
        phone = None,
        email = None,
        mobile = None
      )
      val newContactDetails: ContactDetails = contactDetails.copy(phone = Some("12333334234234"))

      insert(newCTDoc.copy(contactDetails = Some(contactDetails)))
      await(repository.updateContactDetails(registrationId, newContactDetails)) mustBe Some(newContactDetails)
    }

    "return None if no document exists" in new Setup {
      val contactDetails: ContactDetails = ContactDetails(
        phone = None,
        email = None,
        mobile = None
      )

      await(repository.updateContactDetails(registrationId, contactDetails)) mustBe None
    }
  }

  "retrieveContactDetails" must {
    "return none when there is no document" in new Setup {
      await(repository.retrieveContactDetails(registrationId)) mustBe None
    }
    "return none when there are no contact details" in new Setup {
      insert(newCTDoc)
      await(repository.retrieveContactDetails(registrationId)) mustBe None
    }
    "return some when there are contact details" in new Setup {
      val testContactDetails: ContactDetails = ContactDetails(
        phone = Some("123456"),
        email = None,
        mobile = None
      )
      insert(newCTDoc.copy(contactDetails = Some(testContactDetails)))
      await(repository.retrieveContactDetails(registrationId)) mustBe Some(testContactDetails)
    }
  }

  "updateConfirmationReferences" must {
    "return some confirmation references if document exists" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      insert(newCTDoc)
      await(repository.updateConfirmationReferences(registrationId, confirmationReferences)) mustBe Some(confirmationReferences)
    }

    "return override confirmation references if document already has confirmation references" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )
      val newConfirmationReferences: ConfirmationReferences = confirmationReferences.copy(paymentReference = Some("Some-pay-ref"), paymentAmount = Some("12333334234234"))

      insert(newCTDoc.copy(confirmationReferences = Some(confirmationReferences)))
      await(repository.updateConfirmationReferences(registrationId, newConfirmationReferences)) mustBe Some(newConfirmationReferences)
    }

    "return None if no document exists" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      await(repository.updateConfirmationReferences(registrationId, confirmationReferences)) mustBe None
    }
  }

  "retrieveConfirmationReferences" must {
    "return none when there is no document" in new Setup {
      await(repository.retrieveConfirmationReferences(registrationId)) mustBe None
    }
    "return none when there are no confirmation references" in new Setup {
      insert(newCTDoc)
      await(repository.retrieveConfirmationReferences(registrationId)) mustBe None
    }
    "return some when there are confirmation references" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      insert(newCTDoc.copy(confirmationReferences = Some(confirmationReferences)))
      await(repository.retrieveConfirmationReferences(registrationId)) mustBe Some(confirmationReferences)
    }
  }

  "updateConfirmationReferencesAndStatus" must {
    "return some confirmation references if document exists" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      insert(newCTDoc)
      await(repository.updateConfirmationReferencesAndUpdateStatus(registrationId, confirmationReferences, "passed")) mustBe Some(confirmationReferences)
      retrieve(registrationId).get.status mustBe "passed"
    }

    "return override confirmation references if document already has confirmation references and status" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )
      val newConfirmationReferences: ConfirmationReferences = confirmationReferences.copy(paymentReference = Some("Some-pay-ref"), paymentAmount = Some("12333334234234"))

      insert(newCTDoc.copy(confirmationReferences = Some(confirmationReferences), status = "passed"))
      await(repository.updateConfirmationReferencesAndUpdateStatus(registrationId, newConfirmationReferences, "unpassed")) mustBe Some(newConfirmationReferences)
      retrieve(registrationId).get.status mustBe "unpassed"
    }

    "return None if no document exists" in new Setup {
      val confirmationReferences: ConfirmationReferences = ConfirmationReferences(
        acknowledgementReference = "some-ackref",
        transactionId = "some-txid",
        paymentReference = None,
        paymentAmount = None
      )

      await(repository.updateConfirmationReferencesAndUpdateStatus(registrationId, confirmationReferences, "passed")) mustBe None
    }
  }

  "updateCompanyEndDate" must {
    "return some Accounting Details prep if document exists" in new Setup {
      val accountingPrepDetails: AccountPrepDetails = AccountPrepDetails(
        status = "end",
        endDate = None
      )

      insert(newCTDoc)
      await(repository.updateCompanyEndDate(registrationId, accountingPrepDetails)) mustBe Some(accountingPrepDetails)
    }

    "return override accounting Details prep if document already has Accounting Details prep" in new Setup {
      val accountingPrepDetails: AccountPrepDetails = AccountPrepDetails(
        status = "end",
        endDate = None
      )
      val newAccountingPrepDetails: AccountPrepDetails = accountingPrepDetails.copy(endDate = Some(DateTime.now()))

      insert(newCTDoc.copy(accountsPreparation = Some(accountingPrepDetails)))
      await(repository.updateCompanyEndDate(registrationId, newAccountingPrepDetails)) mustBe Some(newAccountingPrepDetails)
    }

    "return None if no document exists" in new Setup {
      val accountingPrepDetails: AccountPrepDetails = AccountPrepDetails(
        status = "end",
        endDate = None
      )

      await(repository.updateCompanyEndDate(registrationId, accountingPrepDetails)) mustBe None
    }
  }

  "updateEmail" must {
    "return some email if document exists" in new Setup {
      val email: Email = Email(
        address = "end@end.end",
        emailType = "type",
        linkSent = false,
        verified = false,
        returnLinkEmailSent = false
      )

      insert(newCTDoc)
      await(repository.updateEmail(registrationId, email)) mustBe Some(email)
    }

    "return override email if document already has email" in new Setup {
      val email: Email = Email(
        address = "end@end.end",
        emailType = "type",
        linkSent = false,
        verified = false,
        returnLinkEmailSent = false
      )
      val newEmail: Email = email.copy(linkSent = true, returnLinkEmailSent = true)

      insert(newCTDoc.copy(verifiedEmail = Some(email)))
      await(repository.updateEmail(registrationId, newEmail)) mustBe Some(newEmail)
    }

    "return None if no document exists" in new Setup {
      val email: Email = Email(
        address = "end@end.end",
        emailType = "type",
        linkSent = false,
        verified = false,
        returnLinkEmailSent = false
      )

      await(repository.updateEmail(registrationId, email)) mustBe None
    }
  }

  "retrieveEmail" must {
    "return none when there is no document" in new Setup {
      await(repository.retrieveEmail(registrationId)) mustBe None
    }
    "return none when there is no email block" in new Setup {
      insert(newCTDoc)
      await(repository.retrieveEmail(registrationId)) mustBe None
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
      await(repository.retrieveEmail(registrationId)) mustBe Some(email)
    }
  }

  "updateRegistrationProgress" must {
    "return some registrationPrgoress if document exists" in new Setup {
      val registrationPrgoress: String = "progress"
      insert(newCTDoc)
      await(repository.updateRegistrationProgress(registrationId, registrationPrgoress)) mustBe Some(registrationPrgoress)
    }

    "return override registration progress if document already has registration progress" in new Setup {
      val registrationProgress: String = "progress"
      val newRegistrationProgress: String = "stopped"

      insert(newCTDoc.copy(registrationProgress = Some(registrationProgress)))
      await(repository.updateRegistrationProgress(registrationId, newRegistrationProgress)) mustBe Some(newRegistrationProgress)
    }

    "return None if no document exists" in new Setup {
      val registrationProgress: String = "progress"

      await(repository.updateRegistrationProgress(registrationId, registrationProgress)) mustBe None
    }
  }
  "removeUnnecessaryRegistrationInformation" must {
    "clear all un-needed data" when {
      "mongo statement executes with no errors" in new Setup {
        val registrationId = "testRegId"

        val corporationTaxRegistrationModel = CorporationTaxRegistration(
          internalId = "testID",
          registrationID = registrationId,
          formCreationTimestamp = "testDateTime",
          language = "en"
        )


        setupCollection(repository, corporationTaxRegistrationModel)
        await(repository.removeUnnecessaryRegistrationInformation(registrationId)) mustBe true

        val corporationTaxRegistration: CorporationTaxRegistration = await(repository.findOneBySelector(repository.regIDSelector(registrationId))).get
        corporationTaxRegistration.verifiedEmail.isEmpty mustBe true
        corporationTaxRegistration.companyDetails.isEmpty mustBe true
        corporationTaxRegistration.accountingDetails.isEmpty mustBe true
        corporationTaxRegistration.registrationID mustBe corporationTaxRegistrationModel.registrationID
        corporationTaxRegistration.status mustBe corporationTaxRegistrationModel.status
        corporationTaxRegistration.lastSignedIn mustBe corporationTaxRegistrationModel.lastSignedIn
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

        setupCollection(repository, corporationTaxRegistrationModel)
        await(repository.removeUnnecessaryRegistrationInformation(registrationId)) mustBe true

        val corporationTaxRegistration: CorporationTaxRegistration = await(repository.findOneBySelector(repository.regIDSelector(registrationId))).get
        corporationTaxRegistration.verifiedEmail.isEmpty mustBe true
        corporationTaxRegistration.companyDetails.isEmpty mustBe true
        corporationTaxRegistration.accountingDetails.isEmpty mustBe true
        corporationTaxRegistration.registrationID mustBe corporationTaxRegistrationModel.registrationID
        corporationTaxRegistration.status mustBe corporationTaxRegistrationModel.status
        corporationTaxRegistration.lastSignedIn mustBe corporationTaxRegistrationModel.lastSignedIn
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

        setupCollection(repository, corporationTaxRegistrationModel)
        await(repository.removeUnnecessaryRegistrationInformation(incorrectRegistrationId)) mustBe true
      }
    }
  }


  "updateSubmissionStatus" must {

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
      await(response) mustBe "held"
    }
    "return an exception when document is missing because mapping on result will not contain status" in new Setup {
      intercept[MissingCTDocument](await(repository.updateSubmissionStatus(registrationId, "testStatus")))
    }
  }

  "removeTaxRegistrationInformation" must {

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
      setupCollection(repository, corporationTaxRegistration)

      val response = repository.removeTaxRegistrationInformation(registrationId)
      await(response) mustBe true
    }
    "remove just company details when contact details and trading details are already empty" in new Setup {
      setupCollection(repository, corporationTaxRegistration.copy(tradingDetails = None, contactDetails = None))

      val response = repository.removeTaxRegistrationInformation(registrationId)
      await(response) mustBe true
    }
    "throw a MissingCTDocument exception when document does not exist" in new Setup {
      intercept[MissingCTDocument](await(repository.removeTaxRegistrationInformation("testRegId")))
    }
  }
  "getInternalId" must {
    "return None when no CT Doc exists" in new Setup {
      intercept[MissingCTDocument](await(repository.getInternalId("testInternalId")))
    }
    "return Some(regId, InternalId) when ct doc exists" in new Setup {
      insert(newCTDoc)
      await(repository.getInternalId(registrationId)) mustBe(newCTDoc.registrationID, newCTDoc.internalId)
    }
  }

  "updateCTRecordWithAcknowledgments" must {

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
      result.getModifiedCount mustBe 0
    }
  }

  "retrieveByAckRef" must {

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
        setupCollection(repository, validHeldCorporationTaxRegistration)

        val result = await(repository.findOneBySelector(repository.ackRefSelector(ackRef))).get
        result mustBe validHeldCorporationTaxRegistration
      }
    }
  }

  "Update registration to submitted (with data)" must {

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
      setupCollection(repository, heldReg)

      val crn = "crn1234"
      val submissionTS = "2001-12-31T12:00:00Z"

      val result = await(repository.updateHeldToSubmitted(heldReg.registrationID, crn, submissionTS))

      result mustBe true

      val someActual = await(repository.findOneBySelector(repository.regIDSelector(heldReg.registrationID)))
      someActual mustBe defined
      val actual = someActual.get
      actual.status mustBe RegistrationStatus.SUBMITTED
      actual.crn mustBe Some(crn)
      actual.submissionTimestamp mustBe Some(submissionTS)
      actual.accountingDetails mustBe None
      actual.accountsPreparation mustBe None
    }

    "fail to update the CRN" in new Setup {

      setupCollection(repository, heldReg.copy(registrationID = "ABC"))

      val crn = "crn1234"
      val submissionTS = "2001-12-31T12:00:00Z"

      intercept[MissingCTDocument] {
        await(repository.updateHeldToSubmitted(heldReg.registrationID, crn, submissionTS))
      }

    }

  }

  "removeTaxRegistrationById" must {
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
      setupCollection(repository, corporationTaxRegistration)

      lazy val response = repository.removeTaxRegistrationById(regId)

      retrieve(regId) mustBe Some(corporationTaxRegistration)
      await(response) mustBe true
      retrieve(regId) mustBe None
    }

    "remove only the data associated with the supplied regId" in new Setup {
      count mustBe 0
      insert(corporationTaxRegistration)
      insert(corporationTaxRegistration.copy(registrationID = "otherRegId"))
      count mustBe 2

      val result = await(repository.removeTaxRegistrationById(regId))

      count mustBe 1
      retrieve(regId) mustBe None
      retrieve("otherRegId") mustBe Some(corporationTaxRegistration.copy(registrationID = "otherRegId"))
    }

    "experience an missing ct doc exception if not document exists" in new Setup {
      intercept[MissingCTDocument](await(repository.removeTaxRegistrationById(regId)))
    }
  }

  "Registration Progress" must {
    val registrationId = UUID.randomUUID.toString

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "intId",
      registrationID = registrationId,
      formCreationTimestamp = "testDateTime",
      language = "en",
      registrationProgress = None
    )

    "be stored in the document correctly" in new Setup {
      setupCollection(repository, corporationTaxRegistration)

      def retrieve = await(repository.findOneBySelector(repository.regIDSelector(registrationId)))

      retrieve.get.registrationProgress mustBe None

      val progress = "In progress"
      await(repository.updateRegistrationProgress(registrationId, progress))

      retrieve.get.registrationProgress mustBe Some(progress)
    }
  }

  "updateRegistrationToHeld" must {

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
      lastSignedIn = dateTime,
      groups = Some(Groups(
        groupRelief = true,
        nameOfCompany = Some(GroupCompanyName("testGroupName", GroupCompanyNameEnum.Other)),
        addressAndType = Some(GroupsAddressAndType(GroupAddressTypeEnum.ALF, BusinessAddress(
          "Line 1",
          "Line 2",
          Some("Telford"),
          Some("Shropshire"),
          Some("ZZ1 1ZZ"),
          None
        ))),
        Some(GroupUTR(Some("1234567890")))
      ))
    )

    "update registration status to held, set confirmation refs and remove trading details, contact details and company details" in new Setup {

      setupCollection(repository, corporationTaxRegistration)

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
        heldTimestamp = heldTs,
        groups = None
      ))

      result mustBe expected
    }
  }

  "retrieveLockedRegIds" must {

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

      setupCollection(repository, corporationTaxRegistration)
      setupCollection(repository, lockedCorporationTaxRegistration)

      val result = await(repository.retrieveLockedRegIDs())

      result mustBe List(lockedRegId)
    }
  }

  "retrieveStatusAndExistenceOfCTUTRByAckRef" must {

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
      result mustBe None
    }
    "retrieve a CTUTR (as a Boolean) when the ackref status is 04" in new Setup {
      setupCollection(repository, corporationTaxRegistration("04", ctutr = true))

      val result = await(repository.retrieveStatusAndExistenceOfCTUTR(ackRef))
      result mustBe Option("04" -> true)
    }
    "retrieve no CTUTR (as a Boolean) when the ackref status is 06" in new Setup {
      setupCollection(repository, corporationTaxRegistration("06", ctutr = false))

      val result = await(repository.retrieveStatusAndExistenceOfCTUTR(ackRef))
      result mustBe Option("06" -> false)
    }
  }

  "updateRegistrationWithAdminCTReference" must {

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

      setupCollection(repository, corporationTaxRegistration("06", ctutr = false))

      val Some(result): Option[CorporationTaxRegistration] = await(repository.updateRegistrationWithAdminCTReference(ackRef, newUtr))

      result mustBe corporationTaxRegistration("06", ctutr = false)
    }

    "not update a registration that does not exist" in new Setup {
      val newUtr = "newUtr"
      val newStatus = "04"

      val result: Option[CorporationTaxRegistration] = await(repository.updateRegistrationWithAdminCTReference(ackRef, newUtr))
      val expected: Option[CorporationTaxRegistration] = None

      result mustBe expected
    }
  }

  "retrieveSessionIdentifiers" must {
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
      sessionIdentifiers = if (alreadyHasSessionIds) Some(SessionIds(defaultSID, defaultCID)) else None
    )

    "retrieve session identifiers" when {
      "a document exists and has identifiers" in new Setup {
        setupCollection(repository, corporationTaxRegistration(regId, alreadyHasSessionIds = true))

        await(repository.retrieveSessionIdentifiers(regId)) mustBe Some(SessionIds(defaultSID, defaultCID))
      }

    }
    "return a None" when {
      "a document exists and does not have identifiers" in new Setup {
        setupCollection(repository, corporationTaxRegistration(regId))

        await(repository.retrieveSessionIdentifiers(regId)) mustBe None
      }
      "there is no Document" in new Setup {
        await(repository.retrieveSessionIdentifiers(regId)) mustBe None
      }
    }
  }

  "storeSessionIdentifiers" must {
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
      sessionIdentifiers = if (alreadyHasSessionIds) Some(SessionIds(defaultSID, defaultCID)) else None
    )

    "successfully write encrpyted identifiers to mongo" when {

      "a document exists" in new Setup {
        setupCollection(repository, corporationTaxRegistration(regId))
        await(repository.findOneBySelector(repository.regIDSelector(regId))) map {
          doc => doc.sessionIdentifiers
        } mustBe Some(None)

        await(repository.storeSessionIdentifiers(regId, sessionId, credId))

        await(repository.findOneBySelector(repository.regIDSelector(regId))) map {
          doc => doc.sessionIdentifiers
        } mustBe Some(Some(SessionIds(sessionId, credId)))
      }
      "a document exists and already has old session Ids" in new Setup {

        setupCollection(repository, corporationTaxRegistration(regId, alreadyHasSessionIds = true))
        await(repository.findOneBySelector(repository.regIDSelector(regId))) map {
          doc => doc.sessionIdentifiers
        } mustBe Some(Some(SessionIds(defaultSID, defaultCID)))


        await(repository.storeSessionIdentifiers(regId, sessionId, credId))

        await(repository.findOneBySelector(repository.regIDSelector(regId))) map {
          doc => doc.sessionIdentifiers
        } mustBe Some(Some(SessionIds(sessionId, credId)))
      }
    }

    "unsuccessful" when {
      "no document exists" in new Setup {
        intercept[NoSuchElementException](await(repository.storeSessionIdentifiers(regId, sessionId, credId)))
      }
    }
  }

  "transactionIdSelector" must {
    "return a mapping between a documents transaction id and the value provided" in new Setup {
      val transactionId = "fakeTransId"
      val mapping = Filters.equal("confirmationReferences.transaction-id", transactionId)
      repository.transIdSelector(transactionId) mustBe mapping
    }
  }

  "updateTransactionId" must {
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

        repository.insert(corporationTaxRegistration(updateFrom))
        await(repository.updateTransactionId(updateFrom, updateTo)) mustBe updateTo
        await(repository.findOneBySelector(repository.regIDSelector(regId))) flatMap {
          doc => doc.confirmationReferences
        } mustBe Some(ConfirmationReferences("ackRef", updateTo, None, None))
      }
    }
    "fail to update a document" when {
      "a document is not present in the database" in new Setup {
        val regId = "registrationId"

        val updateFrom = "updateFrom"
        val updateTo = "updateTo"

        intercept[RuntimeException](await(repository.updateTransactionId(updateFrom, updateTo)))
        await(repository.findOneBySelector(repository.regIDSelector(regId))) flatMap {
          doc => doc.confirmationReferences
        } mustBe None
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
        await(repository.retrieveStaleDocuments(1, 90)) mustBe Nil
      }

      "the database contains 0 documents matching the query when trying to fetch 1" in new Setup {
        insert(registration90DaysOldSubmitted)

        await(repository.retrieveStaleDocuments(1, 91)) mustBe Nil
      }
      "the database contains documents not matching the query - held/locked with payment reference" in new Setup {
        insert(registration90DaysOldHeldWithPaymentRef)
        insert(registration90DaysOldLockedWithPaymentRef)

        await(repository.retrieveStaleDocuments(1, 91)) mustBe Nil
      }
      "the database contains documents not matching the query - held/locked without payment reference and held timestamp is 90 days old" in new Setup {
        insert(registration90DaysOldHeld.copy(heldTimestamp = Some(DateTime.now(DateTimeZone.UTC).minusDays(90))))
        insert(registration90DaysOldLocked.copy(heldTimestamp = Some(DateTime.now(DateTimeZone.UTC).minusDays(90))))

        await(repository.retrieveStaleDocuments(1, 91)) mustBe Nil
      }
    }

    "return the oldest document" when {
      "the database contains 1 document matching the query" in new Setup {
        insert(registration91DaysOldDraft)

        await(repository.retrieveStaleDocuments(1, 90)).head.lastSignedIn.getChronology mustBe List(registration90DaysOldDraft).head.lastSignedIn.getChronology
      }

      "the database contains multiple documents matching the query but the batch size was set to 1" in new Setup {
        insert(registration90DaysOldDraft)
        insert(registration91DaysOldDraft)

        await(repository.retrieveStaleDocuments(1, 90)) mustBe List(registration91DaysOldDraft)
      }
    }

    "return multiple documents in order of document age" when {
      "the database contains multiple documents matching the query" in new Setup {
        insert(registration91DaysOldDraft)
        insert(registration91DaysOldLocked)

        await(repository.retrieveStaleDocuments(2, 90)).toSet mustBe Set(registration91DaysOldLocked, registration91DaysOldDraft)
      }

      "the database contains multiple documents with the same time matching the query" in new Setup {
        insert(registration91DaysOldDraft)
        insert(registration91DaysOldLocked)

        await(repository.retrieveStaleDocuments(2, 90)) must contain theSameElementsAs List(registration91DaysOldDraft, registration91DaysOldLocked)
      }

      "1 of the documents match the query and 3 do not" in new Setup {
        count mustBe 0
        insert(registration90DaysOldDraft)
        insert(registration91DaysOldLocked)
        insert(registration30DaysOldDraft)
        insert(registration90DaysOldSubmitted)

        val res = await(repository.retrieveStaleDocuments(5, 90))
        res.map(_.status) mustBe List("draft")
        res.size mustBe 1
        res mustBe List(registration91DaysOldLocked)
      }
    }

    "not fail and continue" when {
      "an invalid registration document is read" in new Setup {
        val incorrectRegistration = ctRegistrationJson(
          regId = registration91DaysOldDraft.registrationID,
          lastSignedIn = registration91DaysOldDraft.lastSignedIn.getMillis,
          malform = Some(Json.obj("registrationID" -> true))
        )
        repository.insertRaw(incorrectRegistration)
        insert(registration91DaysOldDraft)

        await(repository.retrieveStaleDocuments(2, 90)) mustBe List(registration91DaysOldDraft)
      }
    }
  }

  "returnGroupsBlock" must {
    "return some of groups when it exists in ct" in new Setup {
      val encryptedUTR = CorporationTaxRegistrationFixture.instanceOfCrypto.wts.writes("1234567890")

      val fullGroupJsonEncryptedUTR = Json.parse(
        s"""{
           |"groups": {
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "testGroupName",
           |     "nameType" : "Other"
           |   },
           |   "addressAndType" : {
           |     "addressType" : "ALF",
           |       "address" : {
           |         "line1": "1 abc",
           |         "line2" : "2 abc",
           |         "line3" : "3 abc",
           |         "line4" : "4 abc",
           |         "country" : "country A",
           |         "postcode" : "ZZ1 1ZZ"
           |     }
           |   },
           |   "groupUTR" : {
           |     "UTR" : $encryptedUTR
           |   }
           |  }
           |}
        """.stripMargin)

      val regWithGroups = ctRegistrationJson(
        regId = "123",
        malform = Some(fullGroupJsonEncryptedUTR.as[JsObject])
      )
      repository.insertRaw(regWithGroups)
      count mustBe 1
      val res = await(repository.returnGroupsBlock("123"))
      res.get mustBe validGroupsModel

    }
    "return nothing when no groups block exists in ct doc" in new Setup {
      repository.insertRaw(ctRegistrationJson(regId = "123"))
      count mustBe 1
      val res = await(repository.returnGroupsBlock("123"))
      res mustBe None
    }
    "return exception when no ctDoc exists" in new Setup {
      count mustBe 0
      intercept[Exception](await(repository.returnGroupsBlock("123")))

    }
  }
  "deleteGroupsBlock" must {
    "return true if no exception occurred" in new Setup {
      val encryptedUTR = CorporationTaxRegistrationFixture.instanceOfCrypto.wts.writes("1234567890")

      val fullGroupJsonEncryptedUTR = Json.parse(
        s"""{
           |"groups": {
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "testGroupName",
           |     "nameType" : "Other"
           |   },
           |   "addressAndType" : {
           |     "addressType" : "ALF",
           |       "address" : {
           |         "line1": "1 abc",
           |         "line2" : "2 abc",
           |         "line3" : "3 abc",
           |         "line4" : "4 abc",
           |         "country" : "country A",
           |         "postcode" : "ZZ1 1ZZ"
           |     }
           |   },
           |   "groupUTR" : {
           |     "UTR" : $encryptedUTR
           |   }
           |  }
           |}
        """.stripMargin)
      val regWithGroups = ctRegistrationJson(
        regId = "123",
        malform = Some(fullGroupJsonEncryptedUTR.as[JsObject])
      )

      repository.insertRaw(regWithGroups)
      count mustBe 1
      val res = await(repository.deleteGroupsBlock("123"))
      res mustBe true
      count mustBe 1
      await(repository.returnGroupsBlock("123")) mustBe None
    }
    "return true if no groups block existed in the first place and a delete occurs" in new Setup {
      val regWithoutGroups = ctRegistrationJson(
        regId = "123"
      )
      repository.insertRaw(regWithoutGroups)
      count mustBe 1
      val res = await(repository.deleteGroupsBlock("123"))
      res mustBe true
      await(repository.returnGroupsBlock("123")) mustBe None
    }
    "throw exception if no doc is found" in new Setup {
      count mustBe 0
      intercept[Exception](await(repository.deleteGroupsBlock("123")))
    }
  }

  "updateGroupsBlock" must {
    "return updated group and update block if one exists already" in new Setup {
      val encryptedUTR = CorporationTaxRegistrationFixture.instanceOfCrypto.wts.writes("1234567890")

      val fullGroupJsonEncryptedUTR = Json.parse(
        s"""{
           |"groups": {
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "testGroupName",
           |     "nameType" : "Other"
           |   },
           |   "addressAndType" : {
           |     "addressType" : "ALF",
           |       "address" : {
           |         "line1": "1 abc",
           |         "line2" : "2 abc",
           |         "line3" : "3 abc",
           |         "line4" : "4 abc",
           |         "country" : "country A",
           |         "postcode" : "ZZ1 1ZZ"
           |     }
           |   },
           |   "groupUTR" : {
           |     "UTR" : $encryptedUTR
           |   }
           |  }
           |}
        """.stripMargin)
      val regWithGroups = ctRegistrationJson(
        regId = "123",
        malform = Some(fullGroupJsonEncryptedUTR.as[JsObject])
      )
      repository.insertRaw(regWithGroups)
      count mustBe 1
      val res = await(repository.returnGroupsBlock("123"))
      res mustBe Some(validGroupsModel)
      val resOfUpdate = await(repository.updateGroups("123", Groups(false, None, None, None)))
      resOfUpdate mustBe Groups(false, None, None, None)
    }

    "return groups when an upsert occurs of the block" in new Setup {

      val encryptedUTR = CorporationTaxRegistrationFixture.instanceOfCrypto.wts.writes("1234567890")
      val fullGroupJsonEncryptedUTR = Json.parse(
        s"""{
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "testGroupName",
           |     "nameType" : "Other"
           |   },
           |   "addressAndType" : {
           |     "addressType" : "ALF",
           |       "address" : {
           |         "line1": "1 abc",
           |         "line2" : "2 abc",
           |         "line3" : "3 abc",
           |         "line4" : "4 abc",
           |         "country" : "country A",
           |         "postcode" : "ZZ1 1ZZ"
           |     }
           |   },
           |   "groupUTR" : {
           |     "UTR" : $encryptedUTR
           |   }
           |}
        """.stripMargin)
      insert(corporationTaxRegistration(regId = "123").copy(groups = None))
      count mustBe 1
      await(repository.returnGroupsBlock("123")) mustBe None
      val resOfUpdate = await(repository.updateGroups("123", validGroupsModel))
      resOfUpdate mustBe validGroupsModel
      val groups = await(repository.findOneBySelector(repository.regIDSelector("123"))).get.groups.get
      val groupJson = Json.toJson(groups)(Groups.formats(MongoValidation, app.injector.instanceOf[CryptoSCRS]))
      groupJson mustBe fullGroupJsonEncryptedUTR
    }
    "return groups when same data is inserted twice" in new Setup {
      val encryptedUTR = CorporationTaxRegistrationFixture.instanceOfCrypto.wts.writes("1234567890")

      val fullGroupJsonEncryptedUTR = Json.parse(
        s"""{
           |"groups": {
           |   "groupRelief": true,
           |   "nameOfCompany": {
           |     "name": "testGroupName",
           |     "nameType" : "Other"
           |   },
           |   "addressAndType" : {
           |     "addressType" : "ALF",
           |       "address" : {
           |         "line1": "1 abc",
           |         "line2" : "2 abc",
           |         "line3" : "3 abc",
           |         "line4" : "4 abc",
           |         "country" : "country A",
           |         "postcode" : "ZZ1 1ZZ"
           |     }
           |   },
           |   "groupUTR" : {
           |     "UTR" : $encryptedUTR
           |   }
           |  }
           |}
        """.stripMargin)
      val regWithGroups = ctRegistrationJson(
        regId = "123",
        malform = Some(fullGroupJsonEncryptedUTR.as[JsObject])
      )
      repository.insertRaw(regWithGroups)
      count mustBe 1
      val resOfUpdate = await(repository.updateGroups("123", validGroupsModel))
      resOfUpdate mustBe validGroupsModel
    }

    "return exception if regDoc doesnt exist" in new Setup {
      count mustBe 0
      intercept[Exception](await(repository.updateGroups("123", validGroupsModel)))
    }
  }

  "update" must {
    "update a document with the specified updates" when {
      "the document exists" in new Setup {
        val key = "takeoverDetails"
        val regId = "0123456789"
        val testData = corpTaxRegModel()
        implicit val formats = repository.formats
        val testJson = Json.toJson(testData).as[JsObject]

        val res = for {
          _ <- Future.successful(repository.insertRaw(testJson))
          _ <- repository.update(repository.regIDSelector(regId), key, testTakeoverDetails)
          model <- repository.findOneBySelector(repository.regIDSelector(regId))
        } yield model

        await(res).get.takeoverDetails mustBe Some(testTakeoverDetailsModel)
      }
    }

    "throw a NoSuchElementException" when {
      "the document doesn't exist" in new Setup {
        val key = "takeoverDetails"
        val regId = "0123456789"
        count mustBe 0

        intercept[NoSuchElementException](
          await(repository.update(repository.regIDSelector(regId), key, testTakeoverDetails))
        )
      }
    }
  }
}