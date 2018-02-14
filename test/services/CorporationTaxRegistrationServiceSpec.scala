/*
 * Copyright 2018 HM Revenue & Customs
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

import java.util.UUID

import cats.data.OptionT
import connectors._
import fixtures.{AuthFixture, CorporationTaxRegistrationFixture}
import helpers.MongoMocks
import mocks.SCRSMocks
import models.RegistrationStatus._
import models._
import models.admin.Admin
import models.des._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.concurrent.Eventually
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import repositories._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}

import scala.concurrent.Future

class CorporationTaxRegistrationServiceSpec extends UnitSpec with SCRSMocks with CorporationTaxRegistrationFixture
  with AuthFixture with MongoMocks with LogCapturing with Eventually {

  implicit val hc = HeaderCarrier(sessionId = Some(SessionId("testSessionId")))
  implicit val req = FakeRequest("GET", "/test-path")

  val mockBRConnector: BusinessRegistrationConnector = mock[BusinessRegistrationConnector]
  val mockHeldSubmissionRepository: HeldSubmissionMongoRepository = mock[HeldSubmissionMongoRepository]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockIIConnector: IncorporationInformationConnector = mock[IncorporationInformationConnector]
  val mockDesConnector: DesConnector = mock[DesConnector]

  val dateTime: DateTime = DateTime.parse("2016-10-27T16:28:59.000")

  val regId = "reg-id-12345"
  val transId = "trans-id-12345"
  val timestamp = "2016-10-27T17:06:23.000Z"

  class Setup {
    val service = new CorporationTaxRegistrationService {
      val cTRegistrationRepository: CorporationTaxRegistrationMongoRepository = mockCTDataRepository
      val sequenceRepository: SequenceRepository = mockSequenceRepository
      val stateDataRepository: StateDataRepository = mockStateDataRepository
      val microserviceAuthConnector: AuthConnector = mockAuthConnector
      val brConnector: BusinessRegistrationConnector = mockBRConnector
      val heldSubmissionRepository: HeldSubmissionRepository = mockHeldSubmissionRepository
      val submissionCheckAPIConnector: IncorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
      val auditConnector: AuditConnector = mockAuditConnector
      val incorpInfoConnector: IncorporationInformationConnector = mockIIConnector
      val desConnector: DesConnector = mockDesConnector
      val currentDateTime: DateTime = dateTime
    }

    System.clearProperty("feature.etmpHoldingPen")
    System.clearProperty("feature.registerInterest")

    reset(
      mockCTDataRepository, mockSequenceRepository, mockStateDataRepository, mockAuthConnector, mockBRConnector,
      mockHeldSubmissionRepository, mockIncorporationCheckAPIConnector, mockAuditConnector, mockIIConnector, mockDesConnector
    )

    protected def mockGenerateAckRef(ackRef: Int): OngoingStubbing[_] = SequenceRepositoryMocks.getNext("AcknowledgementID", ackRef)

    val stubbedService = new CorporationTaxRegistrationService {
      val cTRegistrationRepository: CorporationTaxRegistrationMongoRepository = mockCTDataRepository
      val sequenceRepository: SequenceRepository = mockSequenceRepository
      val stateDataRepository: StateDataRepository = mockStateDataRepository
      val microserviceAuthConnector: AuthConnector = mockAuthConnector
      val brConnector: BusinessRegistrationConnector = mockBRConnector
      val heldSubmissionRepository: HeldSubmissionRepository = mockHeldSubmissionRepository
      val submissionCheckAPIConnector: IncorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
      val auditConnector: AuditConnector = mockAuditConnector
      val incorpInfoConnector: IncorporationInformationConnector = mockIIConnector
      val desConnector: DesConnector = mockDesConnector
      val currentDateTime: DateTime = dateTime

      override def isRegistrationDraftOrLocked(regId: String) = Future.successful(true)
      override def submitPartial(rID: String, refs: ConfirmationReferences, admin: Option[Admin] = None)
                                (implicit hc: HeaderCarrier, req: Request[AnyContent]) = Future.successful(refs)
    }
  }

  def corporationTaxRegistration(regId: String = regId,
                                 status: String = DRAFT,
                                 confRefs: Option[ConfirmationReferences] = None): CorporationTaxRegistration = {
    CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = dateTime.toString,
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), None, None, "txid"))),
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
      tradingDetails = Some(TradingDetails("false")),
      status = status,
      confirmationReferences = confRefs
    )
  }

  def partialDesSubmission(ackRef: String, timestamp: String = "2016-10-27T17:06:23.000Z"): JsObject = Json.parse(
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
       |       "telephoneNumber":"123",
       |       "mobileNumber":"123",
       |       "emailAddress":"6email@whatever.com"
       |     }
       |   }
       | }
       |}
      """.stripMargin).as[JsObject]

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

  "handleSubmission" should {

    val ho6RequestBody = ConfirmationReferences("", "testPayRef", Some("testPayAmount"), Some("12"))

    val ackRef = "testAckRef"
    val timestamp = "2016-10-27T17:06:23.000Z"

    val businessRegistration = BusinessRegistration(
      regId,
      timestamp,
      "en",
      Some("Director")
    )

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = dateTime.toString,
      language = "en",
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), None, None, "txid"))),
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
      tradingDetails = Some(TradingDetails("false"))
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
         |       "telephoneNumber":"123",
         |       "mobileNumber":"123",
         |       "emailAddress":"6email@whatever.com"
         |     }
         |   }
         | }
         |}
      """.stripMargin).as[JsObject]

    val heldSubmission = HeldSubmissionData(
      regId,
      ackRef,
      partialDesSubmission.toString()
    )


    "handle the partial submission when there is no held document" in new Setup {

      val confRefs = ho6RequestBody

      val result: ConfirmationReferences = await(stubbedService.handleSubmission(regId, ho6RequestBody))
      result shouldBe confRefs
    }

    "return the confirmation references when document is already Held" in new Setup {

      val confRefs = ConfirmationReferences("", "testPayRef", Some("testPayAmount"), Some("12"))

      when(mockCTDataRepository.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(Option(HELD))))

      when(mockCTDataRepository.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(confRefs)))

      val result = await(service.handleSubmission(regId, ho6RequestBody))
      result shouldBe confRefs
    }

    "return the confirmation references when document is already Held and update it with payment info" in new Setup {

      val backendRefs = ho6RequestBody.copy(acknowledgementReference = "BRCT00000000123", paymentReference = None, paymentAmount = None)
      val confRefs = ConfirmationReferences("BRCT00000000123", "testPayRef", Some("testPayAmount"), Some("12"))

      when(mockCTDataRepository.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(Option(HELD))))

      when(mockCTDataRepository.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(backendRefs)))

      when(mockCTDataRepository.updateConfirmationReferences(eqTo(regId), eqTo(confRefs)))
          .thenReturn(Future.successful(Some(confRefs)))

      val result = await(service.handleSubmission(regId, confRefs))
      result shouldBe confRefs
    }

    "throw an exception when document is in Held but has no confirmation references" in new Setup {
      when(mockCTDataRepository.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(Option(HELD))))
      when(mockCTDataRepository.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(None))

      intercept[RuntimeException](await(service.handleSubmission(regId, ho6RequestBody)))
    }
  }

  "retrieveConfirmationReference" should {

    "return an refs if found" in new Setup {
      val expected = ConfirmationReferences("testTransaction", "testPayRef", Some("testPayAmount"), Some("12"))

      when(mockCTDataRepository.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(expected)))

      val result: Option[ConfirmationReferences] = await(service.retrieveConfirmationReferences(regId))
      result shouldBe Some(expected)
    }

    "return an empty option if an Ack ref is not found" in new Setup {
      when(mockCTDataRepository.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(None))

      val result: Option[ConfirmationReferences] = await(service.retrieveConfirmationReferences(regId))
      result shouldBe None
    }
  }

  "isRegistrationDraftOrLocked" should {

    "return a true when the document status fetched is draft" in new Setup {

      when(mockCTDataRepository.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(Option(DRAFT))))

      val result: Boolean = await(service.isRegistrationDraftOrLocked(regId))
      result shouldBe true
    }

    "return a true when the document status fetched is locked" in new Setup {

      when(mockCTDataRepository.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(Option(LOCKED))))

      val result: Boolean = await(service.isRegistrationDraftOrLocked(regId))
      result shouldBe true
    }

    "return a false when the document status fetched is held" in new Setup {

      when(mockCTDataRepository.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(Option(HELD))))

      val result: Boolean = await(service.isRegistrationDraftOrLocked(regId))
      result shouldBe false
    }

    "return a false when the document status fetched is not held" in new Setup {

      when(mockCTDataRepository.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(Option("otherStatus"))))

      val result: Boolean = await(service.isRegistrationDraftOrLocked(regId))
      result shouldBe false
    }

    "throw a RuntimeException when a registration document can't be found" in new Setup {

      when(mockCTDataRepository.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(None: Option[String])))

      val ex: Exception = intercept[RuntimeException](await(service.isRegistrationDraftOrLocked(regId)))
      ex.getMessage shouldBe s"Registration status not found for regId : $regId"
    }
  }

  "storeConfirmationReferencesAndUpdateStatus" should {

    val confRefs = ConfirmationReferences("testAckRef", "testPayRef", Some("testPayAmount"), Some("12"))

    "return the same confirmation refs that were supplied on a successful store" in new Setup {

      when(mockCTDataRepository.updateConfirmationReferences(eqTo(regId), eqTo(confRefs)))
        .thenReturn(Future.successful(Some(confRefs)))

      val result: ConfirmationReferences = await(service.storeConfirmationReferencesAndUpdateStatus(regId, confRefs, None))
      result shouldBe confRefs
    }

    "throw a RuntimeException when the repository returns a None on an unsuccessful store" in new Setup {

      when(mockCTDataRepository.updateConfirmationReferences(eqTo(regId), eqTo(confRefs)))
        .thenReturn(Future.successful(None))

      val ex: Exception = intercept[RuntimeException](await(service.storeConfirmationReferencesAndUpdateStatus(regId, confRefs, None)))
      ex.getMessage shouldBe s"[HO6] Could not update confirmation refs for regId: $regId - registration document not found"
    }
  }

  "registerInterest" should {

    "return true when the register interest feature flag is enabled and the IIConnector returned true" in new Setup {
      System.setProperty("feature.registerInterest", "true")

      when(mockIIConnector.registerInterest(eqTo(regId), eqTo(transId), any())(any(), any()))
        .thenReturn(Future.successful(true))

      val result: Boolean = await(service.registerInterest(regId, transId))
      result shouldBe true

      verify(mockIIConnector, times(1)).registerInterest(eqTo(regId), eqTo(transId), any())(any(), any())
    }

    "return false when the register interest feature flag is disabled" in new Setup {
      System.setProperty("feature.registerInterest", "false")

      val result: Boolean = await(service.registerInterest(regId, transId))
      result shouldBe false

      verify(mockIIConnector, times(0)).registerInterest(eqTo(regId), eqTo(transId), any())(any(), any())
    }
  }

  "storePartialSubmission" should {

    val ackRef = "ack-ref-12345"

    val partialSubmission = partialDesSubmission(ackRef)

    val dateTime = DateTime.now()

    val heldSubmissionData = HeldSubmissionData(regId, ackRef, partialSubmission.toString, dateTime)

    "send a partial submission to DES when the ETMP feature flag is enabled and return a HeldSubmissionData object when the connector returns a 200 HttpResponse" in new Setup {
      System.setProperty("feature.etmpHoldingPen", "true")

      when(mockDesConnector.ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      val result: HeldSubmissionData = await(service.storePartialSubmission(regId, ackRef, partialSubmission))
      val heldSubmissionWithSameSubmissionTime: HeldSubmissionData = heldSubmissionData.copy(heldTime = result.heldTime)

      result shouldBe heldSubmissionWithSameSubmissionTime

      verify(mockDesConnector, times(1)).ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any())
    }

    "send a partial submission to the Held repository when the ETMP feature flag is disabled and return a HeldSubmissionData object when the repository returns a held submission" in new Setup {
      System.setProperty("feature.etmpHoldingPen", "false")

      when(mockHeldSubmissionRepository.retrieveSubmissionByAckRef(eqTo(ackRef)))
        .thenReturn(Future.successful(None))
      when(mockHeldSubmissionRepository.storePartialSubmission(eqTo(regId), eqTo(ackRef), eqTo(partialSubmission)))
        .thenReturn(Future.successful(Some(heldSubmissionData)))

      await(service.storePartialSubmission(regId, ackRef, partialSubmission)) shouldBe heldSubmissionData

      verify(mockDesConnector, times(0)).ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any())
    }

    "throw a Runtime exception when the ETMP feature flag is disabled and the repository returns a None" in new Setup {
      System.setProperty("feature.etmpHoldingPen", "false")

      when(mockHeldSubmissionRepository.retrieveSubmissionByAckRef(eqTo(ackRef)))
        .thenReturn(Future.successful(None))
      when(mockHeldSubmissionRepository.storePartialSubmission(eqTo(regId), eqTo(ackRef), eqTo(partialSubmission)))
        .thenReturn(Future.successful(None))

      intercept[RuntimeException](await(service.storePartialSubmission(regId, ackRef, partialSubmission)))

      verify(mockDesConnector, times(0)).ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any())
    }
  }

  "retrieveCredId" should {

    val gatewayId = "testGatewayID"
    val userIDs = UserIds("foo", "bar")
    val authority = Authority("testURI", gatewayId, "testUserDetailsLink", userIDs)

    "return the credential id" in new Setup {
      when(mockAuthConnector.getCurrentAuthority()(any()))
        .thenReturn(Future.successful(Some(authority)))

      val result: String = await(service.retrieveCredId)
      result shouldBe gatewayId
    }
  }

  "retrieveBRMetadata" should {

    val businessRegistration = BusinessRegistration(
      regId,
      "testTimeStamp",
      "en",
      Some("Director")
    )

    "return a business registration" in new Setup {
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))

      val result: BusinessRegistration = await(service.retrieveBRMetadata(regId))
      result shouldBe businessRegistration
    }
  }

  "retrieveCTData" should {

    val corporationTaxRegistration = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = regId,
      formCreationTimestamp = "testTimeStamp",
      language = "en"
    )

    "return a CorporationTaxRegistration" in new Setup {
      when(mockCTDataRepository.retrieveCorporationTaxRegistration(eqTo(regId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      val result: CorporationTaxRegistration = await(service.retrieveCTData(regId))
      result shouldBe corporationTaxRegistration
    }
  }

  "buildInterimSubmission" should {

    val ackRef = "testAckRef"
    val sessionId = "testSessionId"
    val credId = "testCredId"

    val businessRegistration = BusinessRegistration(
      regId,
      dateTime.toString,
      "en",
      Some("Director")
    )

    // TODO - refactor and tweak tests - couple more scenarios for optionality
    val companyDetails1 = CompanyDetails(
      "name",
      CHROAddress("P", "1", Some("2"), "C", "L", Some("PO"), Some("PC"), Some("R")),
      PPOB("MANUAL", Some(PPOBAddress("1", "2", Some("3"), Some("4"), Some("ZZ1 1ZZ"), Some("C"), None, "txid"))),
      "J"
    )
    val companyDetails2 = CompanyDetails(
      "name",
      CHROAddress("P", "1", Some("2"), "C", "L", Some("PO"), Some("PC"), Some("R")),
      PPOB("MANUAL", Some(PPOBAddress("1", "1", None, None, Some("ZZ1 1ZZ"), None, None, "txid"))),
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
        tradingDetails = Some(TradingDetails("false"))
      )
    }

    "return a valid InterimDesRegistration with full contact details" in new Setup {

      val ctReg = getCTReg(regId, Some(companyDetails1), Some(contactDetails1))
      val result = service.buildInterimSubmission(ackRef, sessionId, credId, businessRegistration, ctReg, dateTime)

      await(result) shouldBe InterimDesRegistration(
        ackRef,
        Metadata(sessionId, credId, "en", DateTime.parse(service.formatTimestamp(dateTime)), Director),
        InterimCorporationTax(
          "name",
          returnsOnCT61 = false,
          Some(BusinessAddress("1", "2", Some("3"), Some("4"), Some("ZZ1 1ZZ"), Some("C"))),
          BusinessContactName("F", Some("M"), "S"),
          BusinessContactDetails(Some("1"), Some("2"), Some("a@b.c"))
        )
      )
    }

    "return a valid InterimDesRegistration with minimal deatils" in new Setup {

      val ctReg = getCTReg(regId, Some(companyDetails2), Some(contactDetails2))
      val result = service.buildInterimSubmission(ackRef, sessionId, credId, businessRegistration, ctReg, dateTime)

      await(result) shouldBe InterimDesRegistration(
        ackRef,
        Metadata(sessionId, credId, "en", DateTime.parse(service.formatTimestamp(dateTime)), Director),
        InterimCorporationTax(
          "name",
          returnsOnCT61 = false,
          Some(BusinessAddress("1", "1", None, None, Some("ZZ1 1ZZ"), None)),
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
    val authority = Authority("testURI", credId, "testUserDetailsLink", userIDs)

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
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
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
      tradingDetails = Some(TradingDetails("false"))
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
          Some(BusinessAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"))),
          BusinessContactName(
            corporationTaxRegistration.contactDetails.get.firstName,
            corporationTaxRegistration.contactDetails.get.middleName,
            corporationTaxRegistration.contactDetails.get.surname
          ),
          BusinessContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk"))
        )
      )

      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockAuthConnector.getCurrentAuthority()(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(authority)))
      when(mockCTDataRepository.retrieveCorporationTaxRegistration(eqTo(registrationId)))
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
        when(mockCTDataRepository.retrieveByAckRef(eqTo(ackRef)))
          .thenReturn(Future.successful(None))

        val result = await(service.updateCTRecordWithAckRefs(ackRef, refs))
        result shouldBe None
      }
    }

    "return an optional ack ref payload" when {
      "the ct record has been found and subsequently updated" in new Setup {
        when(mockCTDataRepository.retrieveByAckRef(eqTo(ackRef)))
          .thenReturn(Future.successful(Some(validHeldCorporationTaxRegistration)))

        when(mockCTDataRepository.updateCTRecordWithAcknowledgments(eqTo(ackRef), eqTo(updated)))
          .thenReturn(Future.successful(successfulWrite))

        val result = await(service.updateCTRecordWithAckRefs(ackRef, refs))
        result shouldBe Some(validHeldCorporationTaxRegistration)
      }

      "the ct record has been found but has been already updated" in new Setup {
        when(mockCTDataRepository.retrieveByAckRef(eqTo(ackRef)))
          .thenReturn(Future.successful(Some(updated)))

        val result = await(service.updateCTRecordWithAckRefs(ackRef, refs))
        result shouldBe Some(updated)
      }
    }
  }

  "checkDocumentStatus" should {

    val registrationIds = Seq.fill(5)(UUID.randomUUID.toString)

    val heldSubmission = HeldSubmission(
      "registrationId",
      "ackRef",
      Json.obj("foo" -> "bar")
    )

    "log the status of a list of RegIds" in new Setup {

      val res = {
        Seq(corporationTaxRegistration(registrationIds(0), DRAFT),
            corporationTaxRegistration(registrationIds(1), HELD),
            corporationTaxRegistration(registrationIds(2), SUBMITTED),
            corporationTaxRegistration(registrationIds(3), HELD),
            corporationTaxRegistration(registrationIds(4), DRAFT))
      }

      def success(i: Int) = Future.successful(Some(res(i)))

      withCaptureOfLoggingFrom(Logger) { logEvents =>

        when(mockCTDataRepository.retrieveCorporationTaxRegistration(ArgumentMatchers.anyString()))
          .thenReturn(success(0), success(1), success(2), success(3), success(4))

        when(mockHeldSubmissionRepository.retrieveSubmissionByRegId(ArgumentMatchers.anyString()))
          .thenReturn(Future.successful(None), Future.successful(Some(heldSubmission)))

        await(service.checkDocumentStatus(registrationIds))

        eventually {
          logEvents.length shouldBe 5
          logEvents.head.getMessage should include("Current status of regId:")
        }
      }
    }
  }

  "locateOldHeldSubmissions" should {
    val registrationId = "testRegId"
    val tID = "transID"
    val heldTime = Some(DateTime.now().minusWeeks(1))

    val oldHeldSubmission = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = registrationId,
      formCreationTimestamp = dateTime.toString,
      language = "en",
      status = RegistrationStatus.HELD,
      companyDetails = Some(CompanyDetails(
        "testCompanyName",
        CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
        PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
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
      tradingDetails = Some(TradingDetails("false")),
      heldTimestamp = heldTime,
      confirmationReferences = Some(ConfirmationReferences(
        acknowledgementReference = "ackRef",
        transactionId = tID,
        paymentReference = Some("payref"),
        paymentAmount = Some("12")
      ))
    )

    "log nothing and return 'No week old held submissions found' in said case" in new Setup {
      when(mockCTDataRepository.retrieveAllWeekOldHeldSubmissions())
        .thenReturn(Future.successful(List()))

      val result = await(service.locateOldHeldSubmissions)
      result shouldBe "No week old held submissions found"
    }

    "log cases of week old held submissions and output 'Week old held submissions found'" in new Setup {
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        when(mockCTDataRepository.retrieveAllWeekOldHeldSubmissions())
          .thenReturn(Future.successful(List(oldHeldSubmission)))

        val result = await(service.locateOldHeldSubmissions)
        result shouldBe "Week old held submissions found"

        eventually {
          logEvents.length shouldBe 2
          logEvents.head.getMessage should include("ALERT_missing_incorporations")
          logEvents.tail.head.getMessage should
            include(s"Held submission older than one week of regID: $registrationId txID: $tID heldDate: ${heldTime.get.toString})")
        }
      }
    }
  }
}
