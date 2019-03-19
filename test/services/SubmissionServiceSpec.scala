/*
 * Copyright 2019 HM Revenue & Customs
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

import audit.UserRegistrationSubmissionEvent
import cats.data.OptionT
import connectors._
import fixtures.CorporationTaxRegistrationFixture
import mocks.{AuthorisationMocks, SCRSMocks}
import models.RegistrationStatus._
import models._
import models.des._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import reactivemongo.api.commands.{DefaultWriteResult, WriteResult}
import repositories._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}

import scala.concurrent.{ExecutionContext, Future}

class SubmissionServiceSpec extends SCRSMocks with UnitSpec with AuthorisationMocks  with CorporationTaxRegistrationFixture
  with MockitoSugar with LogCapturing with Eventually with BeforeAndAfterEach {


  implicit val hc = HeaderCarrier(sessionId = Some(SessionId("testSessionId")))
  implicit val req = FakeRequest("GET", "/test-path")

  val mockBRConnector: BusinessRegistrationConnector = mock[BusinessRegistrationConnector]
  val mockCorpTaxRepo: CorporationTaxRegistrationRepository = mock[CorporationTaxRegistrationRepository]
  val mockSequenceRepo: SequenceRepository = mock[SequenceRepository]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockIIConnector: IncorporationInformationConnector = mock[IncorporationInformationConnector]
  val mockDesConnector: DesConnector = mock[DesConnector]
  val mockCorpTaxService: CorporationTaxRegistrationService = mock[CorporationTaxRegistrationService]

  val dateTime: DateTime = DateTime.parse("2016-10-27T16:28:59.000")

  val regId = "reg-id-12345"
  val transId = "trans-id-12345"
  val timestamp = "2016-10-27T17:06:23.000Z"
  val authProviderId = "auth-prov-id-12345"

  override def beforeEach(): Unit = {
    reset(
      mockCorpTaxRepo, mockSequenceRepository, mockAuthConnector, mockBRConnector,
      mockIncorporationCheckAPIConnector, mockAuditConnector, mockIIConnector, mockDesConnector
    )
  }
  class Setup {
    val service = new SubmissionService {
      override val cTRegistrationRepository: CorporationTaxRegistrationRepository = mockCorpTaxRepo
      override val sequenceRepository: SequenceRepository = mockSequenceRepository
      override val incorpInfoConnector: IncorporationInformationConnector = mockIIConnector
      override val desConnector: DesConnector = mockDesConnector
      override val auditConnector: AuditConnector = mockAuditConnector
      override val brConnector: BusinessRegistrationConnector = mockBRConnector
      override val corpTaxRegService: CorporationTaxRegistrationService = mockCorpTaxService

      override def currentDateTime: DateTime = dateTime
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
       |     "businessContactDetails":{
       |       "telephoneNumber":"123",
       |       "mobileNumber":"123",
       |       "emailAddress":"6email@whatever.com"
       |     }
       |   }
       | }
       |}
      """.stripMargin).as[JsObject]

  "handleSubmission" should {

    val ho6RequestBody = ConfirmationReferences("", "testPayRef", Some("testPayAmount"), Some("12"))

    "handle the partial submission when the document is not yet Held" in new Setup {

      val confRefs = ho6RequestBody
      val businessRegistration = BusinessRegistration(
        regId,
        "testTimeStamp",
        "en",
        Some("Director")
      )

      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(regId)))
        .thenReturn(Future.successful(Option(corporationTaxRegistration(regId, DRAFT, Some(confRefs)))))

      when(mockCorpTaxRepo.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(confRefs)))

      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))

      when(mockCorpTaxService.retrieveCTData(eqTo(regId)))
        .thenReturn(Future.successful(corporationTaxRegistration(regId, DRAFT, Some(confRefs))))

      when(mockIIConnector.registerInterest(eqTo(regId), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      when(mockDesConnector.ctSubmission(any(), any(), eqTo(regId), any())(any()))
        .thenReturn(Future.successful(HttpResponse(202)))

      when(mockCorpTaxRepo.retrieveCompanyDetails(eqTo(regId)))
        .thenReturn(Future.successful(Some(CompanyDetails("", CHROAddress("", "", None, "", "", None, None, None), PPOB("", None), ""))))

      when(mockCorpTaxRepo.updateRegistrationToHeld(eqTo(regId), eqTo(confRefs)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration(regId, transId, Option(confRefs)))))

      val result: ConfirmationReferences = await(service.handleSubmission(regId, authProviderId, ho6RequestBody, false))
      result shouldBe confRefs
    }

    "return the confirmation references when document is already Held" in new Setup {

      val confRefs = ConfirmationReferences("", "testPayRef", Some("testPayAmount"), Some("12"))

      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(regId)))
        .thenReturn(Future.successful(Option(corporationTaxRegistration(regId, HELD, Some(confRefs)))))

      when(mockCorpTaxRepo.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(confRefs)))

      await(service.handleSubmission(regId, authProviderId, ho6RequestBody, false)) shouldBe confRefs
    }

    "return the confirmation references when document is already Held and update it with payment info" in new Setup {

      val backendRefs = ho6RequestBody.copy(acknowledgementReference = "BRCT00000000123", paymentReference = None, paymentAmount = None)
      val confRefs = ConfirmationReferences("BRCT00000000123", "testPayRef", Some("testPayAmount"), Some("12"))

      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(regId)))
        .thenReturn(Future.successful(Option(corporationTaxRegistration(regId, HELD, Some(confRefs)))))

      when(mockCorpTaxRepo.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(Some(backendRefs)))

      when(mockCorpTaxRepo.updateConfirmationReferences(eqTo(regId), eqTo(confRefs)))
          .thenReturn(Future.successful(Some(confRefs)))

      await(service.handleSubmission(regId, authProviderId, confRefs, false)) shouldBe confRefs
    }

    "throw an exception when document is in Held but has no confirmation references" in new Setup {
      when(mockCorpTaxRepo.fetchDocumentStatus(eqTo(regId)))
        .thenReturn(OptionT(Future.successful(Option(HELD))))
      when(mockCorpTaxRepo.retrieveConfirmationReferences(eqTo(regId)))
        .thenReturn(Future.successful(None))

      intercept[RuntimeException](await(service.handleSubmission(regId, authProviderId, ho6RequestBody, false)))
    }
  }

  "storeConfirmationReferencesAndUpdateStatus" should {

    val confRefs = ConfirmationReferences("testAckRef", "testPayRef", Some("testPayAmount"), Some("12"))

    "return the same confirmation refs that were supplied on a successful store" in new Setup {

      when(mockCorpTaxRepo.updateConfirmationReferences(eqTo(regId), eqTo(confRefs)))
        .thenReturn(Future.successful(Some(confRefs)))

      val result: ConfirmationReferences = await(service.storeConfirmationReferencesAndUpdateStatus(regId, confRefs, None))
      result shouldBe confRefs
    }

    "throw a RuntimeException when the repository returns a None on an unsuccessful store" in new Setup {

      when(mockCorpTaxRepo.updateConfirmationReferences(eqTo(regId), eqTo(confRefs)))
        .thenReturn(Future.successful(None))

      val ex: Exception = intercept[RuntimeException](await(service.storeConfirmationReferencesAndUpdateStatus(regId, confRefs, None)))
      ex.getMessage shouldBe s"[HO6] Could not update confirmation refs for regId: $regId - registration document not found"
    }
  }

  "storePartialSubmission" should {

    val ackRef = "ack-ref-12345"

    val partialSubmission = partialDesSubmission(ackRef)

    val dateTime = DateTime.now()

    "send a partial submission to DES when the ETMP feature flag is enabled and return a HeldSubmissionData object when the connector returns a 200 HttpResponse" in new Setup {
      when(mockDesConnector.ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      val result = await(service.submitPartialToDES(regId, ackRef, partialSubmission, authProviderId))

      result.status shouldBe 200

      verify(mockDesConnector, times(1)).ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any())
    }

    "throw a Runtime exception, save sessionID/credID when the ETMP feature flag is enabled and submission DES to fails on a 400" in new Setup {
      when(mockDesConnector.ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any()))
        .thenReturn(Future.failed(Upstream4xxResponse("fail", 400, 400)))
      when(mockCorpTaxRepo.storeSessionIdentifiers(eqTo(regId), any(), any()))
        .thenReturn(Future.successful(true))

      intercept[Upstream4xxResponse](await(service.submitPartialToDES(regId, ackRef, partialSubmission, authProviderId)))

      verify(mockDesConnector, times(1)).ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any())
    }

    "throw a Runtime exception, save sessionID/credID when the ETMP feature flag is enabled and submission DES to fails on a 500" in new Setup {
      when(mockDesConnector.ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any()))
        .thenReturn(Future.failed(Upstream5xxResponse("fail", 500, 500)))
      when(mockCorpTaxRepo.storeSessionIdentifiers(eqTo(regId), any(), any()))
        .thenReturn(Future.successful(true))

      intercept[Upstream5xxResponse](await(service.submitPartialToDES(regId, ackRef, partialSubmission, authProviderId)))

      verify(mockDesConnector, times(1)).ctSubmission(eqTo(ackRef), eqTo(partialSubmission), eqTo(regId), any())(any())
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
    "return future failed if success response but regId's do not match" in new Setup {
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))

    intercept[Exception](await(service.retrieveBRMetadata("123DoesNotMatch")))
    }
    "return future failed if anything but success response" in new Setup {
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationNotFoundResponse))

      intercept[Exception](await(service.retrieveBRMetadata(regId)))
    }
  }

  "Build partial DES submission" should {

    val registrationId = "testRegId"
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
    val companyDetails3 = CompanyDetails(
      "name",
      CHROAddress("P", "1", Some("2"), "CustomCountry", "L", Some("PO"), Some("ZZ1 1ZZ"), Some("R")),
      PPOB("RO", None),
      "J"
    )
    val contactDetails1 = ContactDetails( Some("1"), Some("2"), Some("a@b.c"))
    val contactDetails2 = ContactDetails(None, None, Some("a@b.c"))

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
        Metadata(sessionId, authProviderId, "en", dateTime, Director),
        InterimCorporationTax(
          corporationTaxRegistration.companyDetails.get.companyName,
          returnsOnCT61 = false,
          Some(BusinessAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"))),
          BusinessContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk"))
        )
      )

      when(mockCorpTaxService.retrieveCTData(any()))
        .thenReturn(Future.successful(corporationTaxRegistration))
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      val result = service.buildPartialDesSubmission(regId, ackRef, authProviderId, businessRegistration, corporationTaxRegistration)
      await(result).toString shouldBe interimDesRegistration.toString
    }

    "return a valid InterimDesRegistration with full contact details" in new Setup {

      when(mockCorpTaxService.retrieveCTData(any()))
        .thenReturn(Future.successful(corporationTaxRegistration))
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      val ctReg = getCTReg(regId, Some(companyDetails1), Some(contactDetails1))
      val result = service.buildPartialDesSubmission(regId, ackRef, credId, businessRegistration, ctReg)

      await(result) shouldBe InterimDesRegistration(
        ackRef,
        Metadata(sessionId, credId, "en", DateTime.parse(service.formatTimestamp(dateTime)), Director),
        InterimCorporationTax(
          "name",
          returnsOnCT61 = false,
          Some(BusinessAddress("1", "2", Some("3"), Some("4"), Some("ZZ1 1ZZ"), Some("C"))),
          BusinessContactDetails(Some("1"), Some("2"), Some("a@b.c"))
        )
      )
    }

    "return a valid InterimDesRegistration with minimal details" in new Setup {

      when(mockCorpTaxService.retrieveCTData(any()))
        .thenReturn(Future.successful(corporationTaxRegistration))
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      val ctReg = getCTReg(regId, Some(companyDetails2), Some(contactDetails2))
      val result = service.buildPartialDesSubmission(regId, ackRef, credId, businessRegistration, ctReg)

      await(result) shouldBe InterimDesRegistration(
        ackRef,
        Metadata(sessionId, credId, "en", DateTime.parse(service.formatTimestamp(dateTime)), Director),
        InterimCorporationTax(
          "name",
          returnsOnCT61 = false,
          Some(BusinessAddress("1", "1", None, None, Some("ZZ1 1ZZ"), None)),
          BusinessContactDetails(None, None, Some("a@b.c"))
        )
      )
    }

    "return a valid InterimDesRegistration with RO address as the PPOB" in new Setup {

      when(mockCorpTaxService.retrieveCTData(any()))
        .thenReturn(Future.successful(corporationTaxRegistration))
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      when(mockCorpTaxService.convertROToPPOBAddress(any()))
        .thenReturn(Some(PPOBAddress("P 1", "2", Some("L"), Some("R"), Some("ZZ1 1ZZ"), Some("CustomCountry"), None, "")))

      val ctReg = getCTReg(regId, Some(companyDetails3), Some(contactDetails2))
      val result = service.buildPartialDesSubmission(regId, ackRef, credId, businessRegistration, ctReg)

      await(result) shouldBe InterimDesRegistration(
        ackRef,
        Metadata(sessionId, credId, "en", DateTime.parse(service.formatTimestamp(dateTime)), Director),
        InterimCorporationTax(
          "name",
          returnsOnCT61 = false,
          Some(BusinessAddress("P 1", "2", Some("L"), Some("R"), Some("ZZ1 1ZZ"), Some("CustomCountry"))),
          BusinessContactDetails(None, None, Some("a@b.c"))
        )
      )
    }

    "return a valid partial DES submission if the sessionID is available in mongo" in new Setup {
      implicit val hc = HeaderCarrier()

      val interimDesRegistration = InterimDesRegistration(
        ackRef,
        Metadata(sessionId, authProviderId, "en", dateTime, Director),
        InterimCorporationTax(
          corporationTaxRegistration.companyDetails.get.companyName,
          returnsOnCT61 = false,
          Some(BusinessAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"))),
          BusinessContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk"))
        )
      )

      when(mockBRConnector.adminRetrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      val result = service.buildPartialDesSubmission(
        regId, ackRef, authProviderId, businessRegistration, corporationTaxRegistration.copy(sessionIdentifiers = Some(SessionIds(sessionId, authProviderId)))
      )
      await(result).toString shouldBe interimDesRegistration.toString
    }

    "throw a RunTime exception if there is no sessionID in the header carrier or mongo" in new Setup {
      implicit val hc = HeaderCarrier()

      val interimDesRegistration = InterimDesRegistration(
        ackRef,
        Metadata(sessionId, authProviderId, "en", dateTime, Director),
        InterimCorporationTax(
          corporationTaxRegistration.companyDetails.get.companyName,
          returnsOnCT61 = false,
          Some(BusinessAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"))),
          BusinessContactDetails(Some("0123456789"), Some("0123456789"), Some("test@email.co.uk"))
        )
      )

      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(eqTo(registrationId)))
        .thenReturn(Future.successful(Some(corporationTaxRegistration)))

      val ctReg = getCTReg(regId, Some(companyDetails1), Some(contactDetails1))
      intercept[RuntimeException](await(service.buildPartialDesSubmission(regId, ackRef, authProviderId, businessRegistration, ctReg)))
    }
  }

  "updateCTRecordWithAckRefs" should {

    val ackRef = "testAckRef"

    val refs = AcknowledgementReferences(Option("aaa"), "bbb", "ccc")

    val updated = validHeldCorporationTaxRegistration.copy(acknowledgementReferences = Some(refs), status = RegistrationStatus.ACKNOWLEDGED)

    "return None" when {
      "the given ack ref cant be matched against a CT record" in new Setup {
        when(mockCorpTaxRepo.retrieveByAckRef(eqTo(ackRef)))
          .thenReturn(Future.successful(None))

        val result = await(service.updateCTRecordWithAckRefs(ackRef, refs))
        result shouldBe None
      }
    }

    "return an optional ack ref payload" when {
      "the ct record has been found and subsequently updated" in new Setup {
        when(mockCorpTaxRepo.retrieveByAckRef(eqTo(ackRef)))
          .thenReturn(Future.successful(Some(validHeldCorporationTaxRegistration)))

        when(mockCorpTaxRepo.updateCTRecordWithAcknowledgments(eqTo(ackRef), eqTo(updated)))
          .thenReturn(Future.successful(DefaultWriteResult(true,1,Seq.empty,None,None,None)))

        val result = await(service.updateCTRecordWithAckRefs(ackRef, refs))
        result shouldBe Some(validHeldCorporationTaxRegistration)
      }
    }
  }

  "setup partial for topup" should {
    val registrationId = "testRegId"
    val tID = "transID"
    val ackRef = "ackRef"
    val companyDetails = CompanyDetails(
      "testCompanyName",
      CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
      PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), Some("test country"), None, "txid"))),
      "testJurisdiction"
    )
    val sessIds = SessionIds(
      sessionId = "sessID",
      credId = "credID"
    )
    val confRefs = ConfirmationReferences(
      acknowledgementReference = ackRef,
      transactionId = tID,
      paymentReference = Some("payref"),
      paymentAmount = Some("12")
    )

    val lockedSubmission = CorporationTaxRegistration(
      internalId = "testID",
      registrationID = registrationId,
      formCreationTimestamp = dateTime.toString,
      language = "en",
      status = RegistrationStatus.LOCKED,
      companyDetails = Some(companyDetails),
      contactDetails = Some(ContactDetails(
        Some("0123456789"),
        Some("0123456789"),
        Some("test@email.co.uk")
      )),
      tradingDetails = Some(TradingDetails("false")),
      heldTimestamp = Some(DateTime.now()),
      confirmationReferences = Some(confRefs),
      sessionIdentifiers = Some(sessIds)
    )

    val businessRegistration = BusinessRegistration(
      registrationId,
      "testTimeStamp",
      "en",
      Some("Director")
    )

    implicit val hc = HeaderCarrier()

    "submit partial if the document is locked" in new Setup {
      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(lockedSubmission)))
      when(mockIIConnector.registerInterest(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCorpTaxRepo.retrieveSessionIdentifiers(any()))
        .thenReturn(Future.successful(Some(sessIds)))
      when(mockBRConnector.adminRetrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(Some(lockedSubmission)))
      when(mockDesConnector.ctSubmission(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))
      when(mockCorpTaxRepo.retrieveCompanyDetails(any()))
        .thenReturn(Future.successful(Some(companyDetails)))
      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any[UserRegistrationSubmissionEvent]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))
      when(mockCorpTaxRepo.updateRegistrationToHeld(eqTo(registrationId), eqTo(confRefs)))
        .thenReturn(Future.successful(Some(lockedSubmission.copy(status = RegistrationStatus.HELD))))
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))

      val result = await(service.setupPartialForTopupOnLocked(tID))
      result shouldBe true
    }

    "submit partial if the document is locked, even if the audit fails" in new Setup {
      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(lockedSubmission)))
      when(mockIIConnector.registerInterest(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())(any(), any()))
          .thenReturn(Future.successful(true))
      when(mockCorpTaxRepo.retrieveSessionIdentifiers(any()))
        .thenReturn(Future.successful(Some(sessIds)))
      when(mockBRConnector.adminRetrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(Some(lockedSubmission)))
      when(mockDesConnector.ctSubmission(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))
      when(mockCorpTaxRepo.retrieveCompanyDetails(any()))
        .thenReturn(Future.successful(Some(companyDetails)))
      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any[UserRegistrationSubmissionEvent]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new RuntimeException))
      when(mockCorpTaxRepo.updateRegistrationToHeld(eqTo(registrationId), eqTo(confRefs)))
        .thenReturn(Future.successful(Some(lockedSubmission.copy(status = RegistrationStatus.HELD))))
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(any(), any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))

      val result = await(service.setupPartialForTopupOnLocked(tID))
      result shouldBe true
    }

    "succeed when trying to submit a partial for a topup if the registration is already submitted" in new Setup {
      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(lockedSubmission.copy(
          status = SUBMITTED
        ))))

      await(service.setupPartialForTopupOnLocked(tID)) shouldBe true
    }

    "succeed when trying to submit a partial for a topup if the registration is already acknowledged" in new Setup {
      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(lockedSubmission.copy(
          status = ACKNOWLEDGED
        ))))

      await(service.setupPartialForTopupOnLocked(tID)) shouldBe true
    }

    "abort processing if the document cannot be found at the audit step" in new Setup {

      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(lockedSubmission)))
      when(mockCorpTaxRepo.retrieveSessionIdentifiers(any()))
        .thenReturn(Future.successful(Some(sessIds)))
      when(mockBRConnector.adminRetrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(Some(lockedSubmission)))
      when(mockDesConnector.ctSubmission(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))
      when(mockCorpTaxRepo.retrieveCompanyDetails(any()))
        .thenReturn(Future.successful(None))

      intercept[RuntimeException](await(service.setupPartialForTopupOnLocked(tID)))
    }

    "fail to submit a partial for a topup if the session identifiers are not present" in new Setup {

      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(lockedSubmission)))
      when(mockCorpTaxRepo.retrieveSessionIdentifiers(any()))
        .thenReturn(Future.successful(None))
      when(mockBRConnector.retrieveMetadata(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(BusinessRegistrationSuccessResponse(businessRegistration)))
      when(mockCorpTaxRepo.retrieveCorporationTaxRegistration(ArgumentMatchers.anyString()))
        .thenReturn(Future.successful(Some(lockedSubmission)))
      when(mockDesConnector.ctSubmission(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))
      when(mockCorpTaxRepo.retrieveCompanyDetails(any()))
        .thenReturn(Future.successful(Some(companyDetails)))

      intercept[RuntimeException](await(service.setupPartialForTopupOnLocked(tID)))
    }

    "fail to submit a partial for a topup if the auth prov ID is not present" in new Setup {
      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(lockedSubmission.copy(sessionIdentifiers = None))))

      intercept[NoSessionIdentifiersInDocument.type](await(service.setupPartialForTopupOnLocked(tID)))
    }

    "fail to submit a partial for a topup if there is no registration" in new Setup {
      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(None))

      intercept[RuntimeException](await(service.setupPartialForTopupOnLocked(tID)))
    }

    "fail to submit a partial for a topup if the registration is not locked" in new Setup {
      when(mockCorpTaxRepo.retrieveRegistrationByTransactionID(any()))
        .thenReturn(Future.successful(Some(lockedSubmission.copy(
            status = RegistrationStatus.DRAFT
      ))))

      intercept[RuntimeException](await(service.setupPartialForTopupOnLocked(tID)))
    }
  }

"setup for prepareDocumentForSubmission" should {

  val registrationId = "testRegId"
  val tID = "transID"
  val ackRef = "ackRef"

  val confRefs = ConfirmationReferences(
    acknowledgementReference = ackRef,
    transactionId = tID,
    paymentReference = Some("payref"),
    paymentAmount = Some("12")
  )

  val lockedSubmission = CorporationTaxRegistration(
    internalId = "testID",
    registrationID = registrationId,
    formCreationTimestamp = "",
    language = "en",
    status = RegistrationStatus.LOCKED,
    confirmationReferences = Some(confRefs)
  )

  val noneSubmission = CorporationTaxRegistration(
    internalId = "testID",
    registrationID = registrationId,
    formCreationTimestamp = "",
    language = "en",
    confirmationReferences = None

  )

  val refsEmpty = ConfirmationReferences(
    acknowledgementReference = ackRef,
    transactionId = tID,
    paymentReference = None,
    paymentAmount = None
  )

  val emptySubmission = CorporationTaxRegistration(
    internalId = "testID",
    registrationID = registrationId,
    formCreationTimestamp = "",
    language = "en",
    status = RegistrationStatus.LOCKED,
    confirmationReferences = Some(refsEmpty)
  )

"throw exception when updateConfirmationReferencesAndUpdateStatus is none " in new Setup{
  when(mockSequenceRepo.getNext(any()))
    .thenReturn(Future.successful(1))
  when(mockCorpTaxRepo.updateConfirmationReferencesAndUpdateStatus(any(), any(), any()))
    .thenReturn(Future.successful(None))

  intercept[Exception](await(service prepareDocumentForSubmission(registrationId, "a", confRefs, noneSubmission)))
}

"successfully return confirmation refs and not change data when updateConfirmationReferencesAndUpdateStatus has data " in new Setup{
    await(service prepareDocumentForSubmission(registrationId, "a", confRefs, lockedSubmission)) shouldBe confRefs
  }

  "successfully update details when confirmationRefsAndPaymentRefsAreEmpty is true " in new Setup{
    when(mockCorpTaxRepo.updateConfirmationReferences(any(), any()))
      .thenReturn(Future.successful(Some(refsEmpty)))

    await(service prepareDocumentForSubmission(registrationId, "a", refsEmpty, emptySubmission)) shouldBe refsEmpty
  }


  }
}
