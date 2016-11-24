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

package services

import connectors.{UserIds, BusinessRegistrationNotFoundResponse, BusinessRegistrationSuccessResponse, Authority, BusinessRegistrationConnector}
import fixtures.{AuthFixture, MongoFixture, CorporationTaxRegistrationFixture}
import helpers.{MongoMocks, SCRSSpec}
import models._
import models.des._
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.{Json, JsObject}
import repositories.{HeldSubmissionMongoRepository, HeldSubmissionData}
import services.CorporationTaxRegistrationService.{FailedToGetBRMetadata, FailedToGetCTData, FailedToGetCredId}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.SessionId
import repositories.Repositories

import scala.concurrent.Future

class CorporationTaxRegistrationServiceSpec extends SCRSSpec with CorporationTaxRegistrationFixture with MongoFixture with AuthFixture with MongoMocks {

  implicit val mongo = mongoDB
  implicit val hc = HeaderCarrier(sessionId = Some(SessionId("testSessionId")))

  val mockBusinessRegistrationConnector = mock[BusinessRegistrationConnector]
  val mockHeldSubmissionRepository = mock[HeldSubmissionMongoRepository]

  val dateTime = DateTime.parse("2016-10-27T16:28:59.000")

  class Setup {
    val service = new CorporationTaxRegistrationService {
      override val corporationTaxRegistrationRepository = mockCTDataRepository
      override val sequenceRepository = mockSequenceRepository
      override val stateDataRepository = mockStateDataRepository
      override val microserviceAuthConnector = mockAuthConnector
      override val brConnector = mockBusinessRegistrationConnector
      override val heldSubmissionRepository = mockHeldSubmissionRepository
      val currentDateTime = dateTime
      override val submissionCheckAPIConnector = mockIncorporationCheckAPIConnector
    }
  }

  "CorporationTaxRegistrationService" should {
    "use the correct CorporationTaxRegistrationRepository" in {
      CorporationTaxRegistrationService.corporationTaxRegistrationRepository shouldBe Repositories.cTRepository
    }
  }

  "createCorporationTaxRegistrationRecord" should {
    "create a new ctData record and return a 201 - Created response" in new Setup {
      CTDataRepositoryMocks.createCorporationTaxRegistration(validDraftCorporationTaxRegistration)

      val result = service.createCorporationTaxRegistrationRecord("54321", "12345", "en")
      await(result) shouldBe validDraftCorporationTaxRegistration
    }
  }

  "retrieveCorporationTaxRegistrationRecord" should {
    "return Corporation Tax registration response Json and a 200 - Ok when a record is retrieved" in new Setup {
      CTDataRepositoryMocks.retrieveCorporationTaxRegistration(Some(validDraftCorporationTaxRegistration))

      val result = service.retrieveCorporationTaxRegistrationRecord("testRegID")
      await(result) shouldBe Some(validDraftCorporationTaxRegistration)
    }

    "return a 404 - Not found when no record is retrieved" in new Setup {
      CTDataRepositoryMocks.retrieveCorporationTaxRegistration(None)

      val result = service.retrieveCorporationTaxRegistrationRecord("testRegID")
      await(result) shouldBe None
    }
  }

  "updateConfirmationReferences" should {
    val registrationId = "testRegId"
    val ackRef = "testAckRef"
    val timestamp = "2016-10-27T17:06:23.000Z"

    val businessRegistration = BusinessRegistration(
      registrationId,
      timestamp,
      "en",
      Some("Director")
    )

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = registrationId,
      formCreationTimestamp = dateTime.toString,
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        ROAddress("10", "test street", "test town", "test area", "test county", "XX1 1ZZ", "test country"),
        PPOBAddress("10", "test street", Some("test town"), Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country")),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName",
        Some("testMiddleName"),
        "testSurname",
        Some("0123456789"),
        Some("0123456789"),
        Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails(false))
    )

    val partialDesSubmission = Json.parse(
      s"""
         |{
         | "acknowledgementReference":"$ackRef",
         | "registration":{
         |   "metadata":{
         |     "businessType":"Limited company",
         |     "submissionFromAgent":false,
         |     "declareAccurateAndComplete":true,
         |     "sessionId":"session-40fdf8c0-e2b1-437c-83b5-8689c2e1bc43",
         |     "credentialId":"cred-id-543212311772",
         |     "language":"en",
         |     "formCreationTimestamp":"$timestamp",
         |     "completionCapacity":"Other",
         |     "completionCapacityOther":"director"
         |   },
         |   "corporationTax":{
         |     "companyOfficeNumber":"001",
         |     "hasCompanyTakenOverBusiness":false,
         |     "companyMemberOfGroup":false,
         |     "companiesHouseCompanyName":"testCompanyName",
         |     "returnsOnCT61":false,
         |     "companyACharity":false,
         |     "businessAddress":{
         |       "line1":"",
         |       "line2":"",
         |       "line3":null,
         |       "line4":null,
         |       "postcode":null,
         |       "country":null
         |     },
         |     "businessContactName":{
         |       "firstName":"Jenifer",
         |       "middleNames":null,
         |       "lastName":null
         |     },
         |     "businessContactDetails":{
         |       "phoneNumber":"123",
         |       "mobileNumber":"123",
         |       "email":"6email@whatever.com"
         |     }
         |   }
         | }
         |}
      """.stripMargin).as[JsObject]

    val heldSubmission = HeldSubmissionData(
      registrationId,
      ackRef,
      partialDesSubmission.toString()
    )

    "return the updated reference acknowledgement number" in new Setup {

      val heldStatus = "held"
      val expected = ConfirmationReferences("testTransaction", "testPayRef", "testPayAmount", "")

      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCTDataRepository.updateConfirmationReferences(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(expected)))
      when(mockAuthConnector.getCurrentAuthority()(Matchers.any()))
        .thenReturn(Future.successful(Some(validAuthority)))
      when(mockCTDataRepository.retrieveCorporationTaxRegistration(Matchers.eq(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))
      when(mockHeldSubmissionRepository.storePartialSubmission(Matchers.eq(registrationId), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Some(heldSubmission)))
      when(mockCTDataRepository.updateSubmissionStatus(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(heldStatus))
      when(mockCTDataRepository.removeTaxRegistrationInformation(registrationId))
        .thenReturn(Future.successful(true))


      SequenceRepositoryMocks.getNext("testSeqID", 3)

      val result = service.updateConfirmationReferences(registrationId, ConfirmationReferences("testTransaction", "testPayRef", "testPayAmount", ""))
      await(result) shouldBe Some(expected)
    }
  }

  "retrieveConfirmationReference" should {
    val regID: String = "testRegID"
    "return an refs if found" in new Setup {
      val expected = ConfirmationReferences("testTransaction", "testPayRef", "testPayAmount", "")

      when(mockCTDataRepository.retrieveConfirmationReference(Matchers.contains(regID)))
        .thenReturn(Future.successful(Some(expected)))

      val result = service.retrieveConfirmationReference(regID)
      await(result) shouldBe Some(expected)
    }
    "return an empty option if an Ack ref is not found" in new Setup {
      when(mockCTDataRepository.retrieveConfirmationReference(Matchers.contains(regID)))
        .thenReturn(Future.successful(None))

      val result = service.retrieveConfirmationReference(regID)
      await(result) shouldBe None
    }
  }

  "retrieveCredId" should {

    implicit val hc = HeaderCarrier(sessionId = Some(SessionId("testSessionId")))

    val userIDs = UserIds("foo", "bar")
    val authority = Authority("testURI", "testGatewayID", "testUserDetailsLink", userIDs)

    "return the credential id" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(Matchers.any()))
        .thenReturn(Future.successful(Some(authority)))

      val result = service.retrieveCredId
      await(result) shouldBe "testGatewayID"
    }

    "return a FailedToGetCredId if an authority cannot be found for the logged in user" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(Matchers.any()))
        .thenReturn(Future.successful(None))

      val result = service.retrieveCredId

      intercept[FailedToGetCredId](await(result))
    }
  }

  "retrieveBRMetadata" should {

    val registrationId = "testRegId"

    implicit val hc = HeaderCarrier()

    val businessRegistration = BusinessRegistration(
      registrationId,
      "testTimeStamp",
      "en",
      Some("Director")
    )

    "return a business registration" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))

      val result = service.retrieveBRMetadata(registrationId)
      await(result) shouldBe businessRegistration
    }

    "return a FailedToGetBRMetadata if a record cannot be found" in new Setup {
      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationNotFoundResponse))

      val result = service.retrieveBRMetadata(registrationId)

      intercept[FailedToGetBRMetadata](await(result))

    }
  }

  "retrieveCTData" should {

    val registrationId = "testRegId"

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = registrationId,
      formCreationTimestamp = "testTimeStamp",
      language = "en"
    )

    "return a CorporationTaxRegistration" in new Setup {
      when(mockCTDataRepository.retrieveCorporationTaxRegistration(Matchers.eq(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      val result = service.retrieveCTData(registrationId)
      await(result) shouldBe corporationTaxRegistration
    }

    "return a FailedToGetCTData when a record cannot be retrieved" in new Setup {
      when(mockCTDataRepository.retrieveCorporationTaxRegistration(Matchers.eq(registrationId)))
        .thenReturn(Future.successful(None))

      val result = service.retrieveCTData(registrationId)
      intercept[FailedToGetCTData](await(result))
    }
  }

  "buildInterimSubmission" should {

    val registrationId = "testRegId"
    val ackRef = "testAckRef"
    val sessionId = "testSessionId"
    val credId = "testCredId"

    val businessRegistration = BusinessRegistration(
      registrationId,
      dateTime.toString,
      "en",
      Some("Director")
    )

    // TODO - refactor and tweak tests - couple more scenarios for optionality
    val companyDetails1 = CompanyDetails(
      "name",
      CHROAddress("P", "1", Some("2"), "C", "L", Some("PO"), Some("PC"), Some("R")),
      ROAddress("H", "1", "2", "3", "4", "ZZ1 1ZZ", "C"),
      PPOBAddress("H", "1", Some("2"), Some("3"), Some("4"), Some("ZZ1 1ZZ"), Some("C")),
      "J"
    )
    val companyDetails2 = CompanyDetails(
      "name",
      CHROAddress("P", "1", Some("2"), "C", "L", Some("PO"), Some("PC"), Some("R")),
      ROAddress("H", "1", "2", "3", "4", "ZZ1 1ZZ", "C"),
      PPOBAddress("H", "1", None, None, None, Some("ZZ1 1ZZ"), None),
      "J"
    )
    val contactDetails1 = ContactDetails("F", Some("M"), "S", Some("1"), Some("2"), Some("a@b.c"))
    val contactDetails2 = ContactDetails("F", None, "S", None, None, Some("a@b.c"))

    def getCTReg(regId: String, company: Option[CompanyDetails], contact: Option[ContactDetails]) = {
      CorporationTaxRegistration(
        internalId = "testID",
        registrationID = regId,
        formCreationTimestamp = dateTime.toString,
        language = "en",
        companyDetails = company,
        contactDetails = contact,
        tradingDetails = Some(TradingDetails(false))
      )
    }

    "return a valid InterimDesRegistration with full contact details" in new Setup {

      val ctReg = getCTReg(registrationId, Some(companyDetails1), Some(contactDetails1))
      val result = service.buildInterimSubmission(ackRef, sessionId, credId, businessRegistration, ctReg, dateTime)

      await(result) shouldBe InterimDesRegistration(
        ackRef,
        Metadata(sessionId, credId, "en", DateTime.parse(service.formatTimestamp(dateTime)), Director),
        InterimCorporationTax(
          "name",
          returnsOnCT61 = false,
          BusinessAddress("H", "1", Some("2"), Some("3"), Some("ZZ1 1ZZ"), Some("C")),
          BusinessContactName("F", Some("M"), "S"),
          BusinessContactDetails(Some("1"), Some("2"), Some("a@b.c"))
        )
      )
    }

    "return a valid InterimDesRegistration with minimal deatils" in new Setup {

      val ctReg = getCTReg(registrationId, Some(companyDetails2), Some(contactDetails2))
      val result = service.buildInterimSubmission(ackRef, sessionId, credId, businessRegistration, ctReg, dateTime)

      await(result) shouldBe InterimDesRegistration(
        ackRef,
        Metadata(sessionId, credId, "en", DateTime.parse(service.formatTimestamp(dateTime)), Director),
        InterimCorporationTax(
          "name",
          returnsOnCT61 = false,
          BusinessAddress("H", "1", None, None, Some("ZZ1 1ZZ"), None),
          BusinessContactName("F", None, "S"),
          BusinessContactDetails(None, None, Some("a@b.c"))
        )
      )
    }
  }

  "Build partial DES submission" should {

    val registrationId = "testRegId"
    val ackRef = "testAckRef"
    val sessionId = "testSessionId"
    val credId = "testCredId"
    val userIDs = UserIds("foo", "bar")
    val authority = Authority("testURI", credId, "testUserDetailsLink",  userIDs)

    val businessRegistration = BusinessRegistration(
      registrationId,
      "testTimeStamp",
      "en",
      Some("Director")
    )

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = registrationId,
      formCreationTimestamp = dateTime.toString,
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        ROAddress("10", "test street", "test town", "test area", "test county", "XX1 1ZZ", "test country"),
        PPOBAddress("10", "test street", Some("test town"), Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country")),
        "testJurisdiction"
      )),
      contactDetails = Some(ContactDetails(
        "testFirstName",
        Some("testMiddleName"),
        "testSurname",
        Some("0123456789"),
        Some("0123456789"),
        Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails(false))
    )

    // TODO - refactor and tweak tests (focus this on the complete partial)
    "return a valid partial DES submission" in new Setup {
      implicit val hc = HeaderCarrier(sessionId = Some(SessionId("testSessionId")))

      val interimDesRegistration = InterimDesRegistration(
        ackRef,
        Metadata(sessionId, credId, "en", dateTime, Director),
        InterimCorporationTax(
          corporationTaxRegistration.companyDetails.get.companyName,
          returnsOnCT61 = false,
          BusinessAddress("10", "test street", Some("test town"), Some("test area"), Some("XX1 1ZZ"), Some("test country")),
          BusinessContactName(
            corporationTaxRegistration.contactDetails.get.firstName,
            corporationTaxRegistration.contactDetails.get.middleName,
            corporationTaxRegistration.contactDetails.get.surname
          ),
          BusinessContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk"))
        )
      )

      when(mockBusinessRegistrationConnector.retrieveMetadata(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockAuthConnector.getCurrentAuthority()(Matchers.any()))
        .thenReturn(Future.successful(Some(authority)))
      when(mockCTDataRepository.retrieveCorporationTaxRegistration(Matchers.eq(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      val result = service.buildPartialDesSubmission(registrationId, ackRef)
      await(result).toString shouldBe interimDesRegistration.toString
    }
  }


  "updateCTRecordWithAckRefs" should {

    val ackRef = "testAckRef"

    val refs = AcknowledgementReferences("aaa", "bbb", "ccc")

    val updated = validHeldCorporationTaxRegistration.copy(acknowledgementReferences = Some(refs), status = "acknowledged")

    val successfulWrite = mockWriteResult()

    "return None" when {
      "the given ack ref cant be matched against a CT record" in new Setup {
        when(mockCTDataRepository.getHeldCTRecord(Matchers.eq(ackRef)))
          .thenReturn(Future.successful(None))

        val result = await(service.updateCTRecordWithAckRefs(ackRef, refs))
        result shouldBe None
      }
    }

    "return an optional ack ref payload" when {
      "the ct record has been found and subsequently updated" in new Setup {
        when(mockCTDataRepository.getHeldCTRecord(Matchers.eq(ackRef)))
          .thenReturn(Future.successful(Some(validHeldCorporationTaxRegistration)))

        when(mockCTDataRepository.updateCTRecordWithAcknowledgments(Matchers.eq(ackRef), Matchers.eq(updated)))
          .thenReturn(Future.successful(successfulWrite))

        val result = await(service.updateCTRecordWithAckRefs(ackRef, refs))
        result shouldBe Some(validHeldCorporationTaxRegistration)
      }

      "the ct record has been found but has been already updated" in new Setup {
        when(mockCTDataRepository.getHeldCTRecord(Matchers.eq(ackRef)))
          .thenReturn(Future.successful(Some(updated)))

        val result = await(service.updateCTRecordWithAckRefs(ackRef, refs))
        result shouldBe Some(updated)
      }
    }
  }
}
