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

import java.util.{Base64, UUID}

import audit.{DesSubmissionEvent, DesTopUpSubmissionEvent, RegistrationAuditEvent, SuccessfulIncorporationAuditEvent}
import connectors._
import fixtures.CorporationTaxRegistrationFixture
import models.{AccountPrepDetails, AccountingDetails, CorporationTaxRegistration, Email, IncorpUpdate, SubmissionCheckResponse, SubmissionDates}
import org.jboss.netty.handler.codec.base64.Base64Decoder
import org.joda.time.DateTime
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, JsPath, JsString, Json}
import repositories.{CorporationTaxRegistrationRepository, HeldSubmission, HeldSubmissionRepository, StateDataRepository}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
//import services.RegistrationHoldingPenService.MissingAccountingDates
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, InternalServerException, Upstream4xxResponse }

class RegistrationHoldingPenServiceSpec extends UnitSpec with MockitoSugar with CorporationTaxRegistrationFixture with BeforeAndAfterEach with Eventually {

  val mockStateDataRepository = mock[StateDataRepository]
  val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  val mockCTRepository = mock[CorporationTaxRegistrationRepository]
  val mockHeldRepo = mock[HeldSubmissionRepository]
  val mockAccountService = mock[AccountingDetailsService]
  val mockDesConnector = mock[DesConnector]
  val mockBRConnector = mock[BusinessRegistrationConnector]
  val mockAuthConnector = mock[AuthConnector]
  val mockAuditConnector = mock[AuditConnector]
  val mockSendEmailService = mock[SendEmailService]

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = {
    reset(mockAuthConnector)
    reset(mockAuditConnector)
    reset(mockStateDataRepository)
    reset(mockIncorporationCheckAPIConnector)
    reset(mockCTRepository)
    reset(mockHeldRepo)
    reset(mockDesConnector)
    reset(mockBRConnector)
  }

  trait mockService extends RegistrationHoldingPenService {
    val stateDataRepository = mockStateDataRepository
    val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
    val ctRepository = mockCTRepository
    val heldRepo = mockHeldRepo
    val accountingService = mockAccountService
    val desConnector = mockDesConnector
    val brConnector = mockBRConnector
    val auditConnector = mockAuditConnector
    val microserviceAuthConnector = mockAuthConnector
    val sendEmailService = mockSendEmailService
    override val addressLine4FixRegID: String = "false"
    override val amendedAddressLine4: String = ""
    override val blockageLoggingDay : String = "MON,TUE"
    override val blockageLoggingTime : String = "08:00:00_17:00:00"
  }

  trait Setup {
    val service = new mockService {}
  }

  trait SetupMockedAudit {
    val service = new mockService {
      override def processSuccessDesResponse(item: IncorpUpdate, ctReg: CorporationTaxRegistration, auditDetail: JsObject, isAdmin:Boolean)(implicit hc: HeaderCarrier) = Future.successful(true)
    }
  }

  class SetupWithAddressLine4Fix(regId: String, addressLine4: String) {
    val service = new mockService {
      override val addressLine4FixRegID: String = regId
      override val amendedAddressLine4: String = addressLine4
    }
  }

  def date(year: Int, month: Int, day: Int) = new DateTime(year,month,day,0,0)
  def date(yyyyMMdd:String) = DateTime.parse(yyyyMMdd)

  implicit val hc = HeaderCarrier()

  val timepoint = "123456789"
  val testAckRef = UUID.randomUUID.toString
  val testRegId = UUID.randomUUID.toString
  val transId = UUID.randomUUID().toString
  val validCR = validHeldCTRegWithData(ackRef=Some(testAckRef)).copy(
    accountsPreparation = Some(AccountPrepDetails(AccountPrepDetails.COMPANY_DEFINED,Some(date("2017-01-01")))), verifiedEmail = Some(Email("testemail.com","",true,true,true))
  )
  import models.RegistrationStatus._
  val submittedCR = validCR.copy(status = SUBMITTED)
  val failCaseCR = validCR.copy(status = DRAFT)
  val incorpSuccess = IncorpUpdate(transId, "accepted", Some("012345"), Some(new DateTime(2016, 8, 10, 0, 0)), timepoint)
  val incorpRejected = IncorpUpdate(transId, "rejected", None, None, timepoint, Some("testReason"))
  val submissionCheckResponseSingle = SubmissionCheckResponse(Seq(incorpSuccess), "testNextLink")
  val submissionCheckResponseDouble = SubmissionCheckResponse(Seq(incorpSuccess,incorpSuccess), "testNextLink")
  val submissionCheckResponseNone = SubmissionCheckResponse(Seq(), "testNextLink")

  def sub(a:String, others:Option[(String, String, String, String)] = None) = {
    val extra = others match {
      case None => ""
      case Some((crn, active, firstPrep, intended)) =>
        s"""
           |  ,
           |  "crn" : "${crn}",
           |  "companyActiveDate": "${active}",
           |  "startDateOfFirstAccountingPeriod": "${firstPrep}",
           |  "intendedAccountsPreparationDate": "${intended}"
           |""".stripMargin
    }

    s"""{  "acknowledgementReference" : "${a}",
        |  "registration" : {
        |  "metadata" : {
        |  "businessType" : "Limited company",
        |  "sessionId" : "session-123",
        |  "credentialId" : "cred-123",
        |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
        |  "submissionFromAgent": false,
        |  "language" : "ENG",
        |  "completionCapacity" : "Director",
        |  "declareAccurateAndComplete": true
        |  },
        |  "corporationTax" : {
        |  "companyOfficeNumber" : "001",
        |  "hasCompanyTakenOverBusiness" : false,
        |  "companyMemberOfGroup" : false,
        |  "companiesHouseCompanyName" : "FooBar",
        |  "returnsOnCT61" : false,
        |  "companyACharity" : false,
        |  "businessAddress" : {"line1" : "1 FooBar Avenue", "line2" : "Bar", "line3" : "Foo Town",
        |  "line4" : "Fooshire", "postcode" : "ZZ1 1ZZ", "country" : "United Kingdom"},
        |  "businessContactName" : {"firstName" : "Foo","middleNames" : "Wibble","lastName" : "Bar"},
        |  "businessContactDetails" : {"phoneNumber" : "0123457889","mobileNumber" : "07654321000","email" : "foo@bar.com"}
        |  ${extra}
        |  }
        |  }
        |}""".stripMargin
  }

  def topUpSub(s: String, a:String, crn:String, active:String, firstPrep:String, intended:String) = {

        s"""{
           |  "status" : "${s}",
           |  "acknowledgementReference" : "${a}",
           |  "corporationTax" : {
           |  "crn" : "${crn}",
           |  "companyActiveDate": "${active}",
           |  "startDateOfFirstAccountingPeriod": "${firstPrep}",
           |  "intendedAccountsPreparationDate": "${intended}"
           |  }
           |  }
           |""".stripMargin
    }

  def topUpRejSub(s: String, a:String) = {

    s"""{
        |  "status" : "${s}",
        |  "acknowledgementReference" : "${a}"
        |  }
        |""".stripMargin
  }

  val crn = "012345"
  val exampleDate = "2012-12-12"
  val exampleDate1 = "2020-05-10"
  val exampleDate2 = "2025-06-06"
  val dates = SubmissionDates(date(exampleDate), date(exampleDate1), date(exampleDate2))
  val acceptedStatus = "Accepted"
  val rejectedStatus = "Rejected"
  val interimSubmission = Json.parse(sub(testAckRef)).as[JsObject]
  val validDesSubmission = Json.parse(sub(testAckRef,Some((crn,exampleDate,exampleDate1,exampleDate2)))).as[JsObject]
  val validTopUpDesSubmission = Json.parse(topUpSub(acceptedStatus,testAckRef,crn,exampleDate,exampleDate1,exampleDate2)).as[JsObject]
  val validRejectedTopUpDesSubmission = Json.parse(topUpRejSub(rejectedStatus,testAckRef)).as[JsObject]

  val validHeld = HeldSubmission(testRegId, testAckRef, interimSubmission)

  "formatDate" should {
    "format a DateTime timestamp into the format yyyy-mm-dd" in new Setup {
      val date = DateTime.parse("1970-01-01T00:00:00.000Z")
      service.formatDate(date) shouldBe "1970-01-01"
    }
  }

  "appendDataToSubmission" should {
    "be able to add final Json additions to the PartialSubmission" in new Setup {
      val result = service.appendDataToSubmission(crn, dates, interimSubmission)

      result shouldBe validDesSubmission
    }
  }

  "buildTopUp Accepted Submission" should {
    "be able to create Json according to the Schema of API4" in new Setup {
      val result = service.buildTopUpSubmission(crn, acceptedStatus, dates, testAckRef)

      result shouldBe validTopUpDesSubmission
    }
  }

  "buildTopUp Rejection Submission" should {
    "be able to create rejected Json according to the Schema of API4" in new Setup {
      val result = service.buildTopUpRejectionSubmission(rejectedStatus, testAckRef)

      result shouldBe validRejectedTopUpDesSubmission
    }
  }

  "checkSubmission" should {
    "return a submission if a timepoint was retrieved successfully" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseSingle))

      await(service.fetchIncorpUpdate) shouldBe submissionCheckResponseSingle.items
    }

    "return a submission if a timepoint was not retrieved" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(None))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(None))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseSingle))

      await(service.fetchIncorpUpdate) shouldBe submissionCheckResponseSingle.items
    }
  }

  "fetchHeldData" should {
    //import RegistrationHoldingPenService.FailedToRetrieveByAckRef

    val regId = "reg-12345"
    val ackRef = "ack-12345"
    val addressLine4 = "testAL4"
    val encodedAddressLine4 = "dGVzdEFMNA=="

    "return a valid Held record found" in new Setup {
      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      val result = service.fetchHeldData(testAckRef)
      await(result.get) shouldBe validHeld
    }

    "return an amended address line 4 held record if the regId from config matces the documents" in new SetupWithAddressLine4Fix(regId, encodedAddressLine4) {
      val held = HeldSubmission(regId, ackRef, heldJson)

      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.any()))
        .thenReturn(Future.successful(Some(held)))

      val result = await(service.fetchHeldData(ackRef))

      result shouldNot be(held)
      (result.get.submission \ "registration" \ "corporationTax" \ "businessAddress" \ "line4").toOption shouldBe Some(JsString(addressLine4))
    }
  }

  "activeDates" should {
    "return DoNotIntendToTrade if that was selected" in new Setup {
      import AccountingDetails.NOT_PLANNING_TO_YET
      service.activeDate(AccountingDetails(NOT_PLANNING_TO_YET, None), incorpSuccess.incorpDate.get) shouldBe DoNotIntendToTrade
    }
    "return ActiveOnIncorporation if that was selected" in new Setup {
      import AccountingDetails.WHEN_REGISTERED
      service.activeDate(AccountingDetails(WHEN_REGISTERED, None), incorpSuccess.incorpDate.get) shouldBe ActiveOnIncorporation
    }
    "return ActiveOnIncorporation if the active date is before the incorporation date" in new Setup {
      import AccountingDetails.FUTURE_DATE
      val tradeDate = "2016-08-09"
      service.activeDate(AccountingDetails(FUTURE_DATE, Some(tradeDate)), incorpSuccess.incorpDate.get) shouldBe ActiveOnIncorporation
    }
    "return ActiveInFuture with a date if that was selected" in new Setup {
      import AccountingDetails.FUTURE_DATE
      val tradeDate = "2017-01-01"
      service.activeDate(AccountingDetails(FUTURE_DATE, Some(tradeDate)), incorpSuccess.incorpDate.get) shouldBe ActiveInFuture(date(tradeDate))
    }
  }

  "calculateDates" should {

    "return valid dates if correct detail is passed" in new Setup {
      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      val result = service.calculateDates(incorpSuccess, validCR.accountingDetails, validCR.accountsPreparation)

      await(result) shouldBe dates
    }
//    "return MissingAccountingDates if no accounting dates are passed" in new Setup {
//      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
//        .thenReturn(dates)
//
//      val result = service.calculateDates(incorpSuccess, None, validCR.accountsPreparation)
//
//      intercept[MissingAccountingDates](await(result))
//    }
  }


  "fetchRegistrationByTxIds" should {
   // import RegistrationHoldingPenService.FailedToRetrieveByTxId

    "return a CorporationTaxRegistration document when one is found by Transaction ID" in new Setup{
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      await(service.fetchRegistrationByTxId(transId)) shouldBe validCR

    }
//    "return a FailedToRetrieveByTxId when one is not found" in new Setup {
//      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
//        .thenReturn(Future.successful(None))
//
//      intercept[FailedToRetrieveByTxId](await(service.fetchRegistrationByTxId(transId)))
//    }
  }

  "updateHeldSubmission" should {

    val testUserDetails = UserDetailsModel("bob", "a@b.c", "organisation", Some("description"), Some("lastName"), Some("1/1/1990"), Some("PO1 1ST"), "123", "456")


    "return a true for a DES ready full submission" in new Setup {

      when(mockAuthConnector.getUserDetails(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(testUserDetails)))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockCTRepository.updateHeldToSubmitted(Matchers.eq(validCR.registrationID), Matchers.eq(incorpSuccess.crn.get), Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockHeldRepo.removeHeldDocument(Matchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.ctSubmission(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID)) shouldBe true

      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])

      verify(mockAuditConnector, times(2)).sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())

      val audit = captor.getAllValues

      audit.get(0).auditType shouldBe "successIncorpInformation"
      (audit.get(0).detail \ "incorporationDate").as[String] shouldBe "2016-08-10"

      audit.get(1).auditType shouldBe "ctRegistrationSubmission"

    }

    "return a true for a DES ready top up submission" in new Setup {

      when(mockAuthConnector.getUserDetails(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(testUserDetails)))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(None))

      when(mockCTRepository.updateHeldToSubmitted(Matchers.eq(validCR.registrationID), Matchers.eq(incorpSuccess.crn.get), Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.topUpCTSubmission(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID)) shouldBe true

      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])

      eventually {
        verify(mockAuditConnector, times(1)).sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())
      }
      val audit = captor.getAllValues

      audit.get(0).auditType shouldBe "ctRegistrationAdditionalData"
//      (audit.get(0).detail \ "incorporationDate").as[String] shouldBe "2016-08-10"

//      audit.get(1).auditType shouldBe "ctRegistrationSubmission"

    }

    "return a true for a DES ready top up submission when audit fails" in new Setup {

      when(mockAuthConnector.getUserDetails(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(testUserDetails)))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new RuntimeException("Audit failed")))

      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(None))

      when(mockCTRepository.updateHeldToSubmitted(Matchers.eq(validCR.registrationID), Matchers.eq(incorpSuccess.crn.get), Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.topUpCTSubmission(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID)) shouldBe true

      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])

      eventually {
        verify(mockAuditConnector, times(1)).sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())
      }
      val audit = captor.getAllValues

      audit.get(0).auditType shouldBe "ctRegistrationAdditionalData"

    }



    "fail if DES states not found on a full submission" in new Setup {
      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.ctSubmission(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("", 404, 400)))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any[SuccessfulIncorporationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      intercept[Upstream4xxResponse] {
        await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID))
      }
    }

    "fail if DES states not found on a top up submission" in new Setup {
      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(None))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.topUpCTSubmission(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("", 404, 400)))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any[SuccessfulIncorporationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(Success))

      intercept[Upstream4xxResponse] {
        await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID))
      }
    }

    "fail if DES states the client request failed" in new Setup {
      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.ctSubmission(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("", 499, 502)))

      intercept[Upstream4xxResponse] {
        await(service.updateHeldSubmission(incorpSuccess, validCR, validCR.registrationID))
      }
    }

    "fail if missing ackref" in new Setup {
      intercept[MissingAckRef] {
        await(service.updateHeldSubmission(incorpSuccess, validCR.copy(confirmationReferences = None), validCR.registrationID))
      }.message should endWith (s"""tx_id "${transId}".""")
    }
  }

  "updateSubmission" should {
    trait SetupNoProcess {
      val service = new mockService {
        implicit val hc = new HeaderCarrier()

        override def updateHeldSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, journeyId : String, isAdmin: Boolean = false)(implicit hc : HeaderCarrier) = Future.successful(true)
        override def updateSubmittedSubmission(ctReg: CorporationTaxRegistration) = Future.successful(true)
      }
    }
    "return true for a DES ready submission" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      await(service.updateSubmissionWithIncorporation(incorpSuccess, validCR)) shouldBe true
    }
    "return true for a submission that is already 'Submitted" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(submittedCR)))

      await(service.updateSubmissionWithIncorporation(incorpSuccess, submittedCR)) shouldBe true
    }
    "return false for a submission that is neither 'Held' nor 'Submitted'" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(failCaseCR)))

      intercept[UnexpectedStatus]{await(service.updateSubmissionWithIncorporation(incorpSuccess, failCaseCR))}
    }

  }

  "updateNextSubmissionByTimepoint" should {
    val expected = Json.obj("key" -> timepoint)
    trait SetupNoProcess {
      val service = new mockService {
        override def processIncorporationUpdate(item: IncorpUpdate, isAdmin: Boolean = false)(implicit hc: HeaderCarrier) = Future.successful(true)
      }
    }

    "return the first Timepoint for a single incorp update" in new SetupNoProcess {

      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseSingle))

      when(mockStateDataRepository.updateTimepoint(Matchers.eq(timepoint)))
          .thenReturn(Future.successful(timepoint))

      val result = await(service.updateNextSubmissionByTimepoint)

      val tp = result.split(" ").reverse.head
      tp.length shouldBe 9
      tp shouldBe timepoint
    }

    "return the first Timepoint for a response with two incorp updates" in new SetupNoProcess {

      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseDouble))

      when(mockStateDataRepository.updateTimepoint(Matchers.eq(timepoint)))
        .thenReturn(Future.successful(timepoint))

      val result = await(service.updateNextSubmissionByTimepoint)

      val tp = result.split(" ").reverse.head
      tp.length shouldBe 9
      tp shouldBe timepoint
    }

    "return a Json.object when there's no incorp updates" in new SetupNoProcess {

      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponseNone))

      val result = await(service.updateNextSubmissionByTimepoint)

      result shouldBe "No Incorporation updates were fetched"
    }

  }

  "processIncorporationUpdate" should {

    trait SetupBoolean {
      val serviceTrue = new mockService {
        override def updateSubmissionWithIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration, isAdmin: Boolean = false)(implicit hc : HeaderCarrier) = Future.successful(true)
      }

      val serviceFalse = new mockService {
        override def updateSubmissionWithIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration, isAdmin: Boolean = false)(implicit hc : HeaderCarrier) = Future.successful(false)
      }
    }

    "return a future true when processing an accepted incorporation" in new SetupBoolean {
//      when(mockAuditConnector.sendExtendedEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
//        .thenReturn(Future.successful(Success))
//
//      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])

      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))
      when(mockSendEmailService.sendVATEmail(Matchers.eq("testemail.com"))(Matchers.any[HeaderCarrier]())).thenReturn(Future.successful(true))
      await(serviceTrue.processIncorporationUpdate(incorpSuccess)) shouldBe true
//
//      verify(mockAuditConnector, times(1)).sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())
//
//      val audit = captor.getValue
//      audit.auditType shouldBe "successIncorpInformation"

    }

    "return a FailedToUpdateSubmissionWithAcceptedIncorp when processing an accepted incorporation returns a false" in new SetupBoolean {
//      when(mockAuditConnector.sendExtendedEvent(Matchers.any[RegistrationAuditEvent]())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]()))
//        .thenReturn(Future.successful(Success))

//      val captor = ArgumentCaptor.forClass(classOf[RegistrationAuditEvent])
      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))
      
      intercept[FailedToUpdateSubmissionWithAcceptedIncorp.type](await(serviceFalse.processIncorporationUpdate(incorpSuccess)) shouldBe false)

//      verify(mockAuditConnector, times(1)).sendExtendedEvent(captor.capture())(Matchers.any[HeaderCarrier](), Matchers.any[ExecutionContext]())
//
//      val audit = captor.getValue
//      audit.auditType shouldBe "successIncorpInformation"


    }
    "return a future true when processing a rejected incorporation with held data" in new Setup{

      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      when(mockCTRepository.updateSubmissionStatus(Matchers.any(), Matchers.any())).thenReturn(Future.successful("rejected"))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      when(mockHeldRepo.removeHeldDocument(Matchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockCTRepository.removeTaxRegistrationById(Matchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockBRConnector.removeMetadata(Matchers.eq(validCR.registrationID))(Matchers.any()))
        .thenReturn(Future.successful(true))

      await(service.processIncorporationUpdate(incorpRejected)) shouldBe true
    }

    "return a future true when processing a rejected incorporation without held data" in new Setup{

      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(None))

      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      when(mockCTRepository.updateSubmissionStatus(Matchers.any(), Matchers.any())).thenReturn(Future.successful("rejected"))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      when(mockDesConnector.topUpCTSubmission(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      when(mockCTRepository.removeTaxRegistrationById(Matchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockBRConnector.removeMetadata(Matchers.eq(validCR.registrationID))(Matchers.any()))
        .thenReturn(Future.successful(true))

      await(service.processIncorporationUpdate(incorpRejected)) shouldBe true
    }

    "return an exception when processing a rejected incorporation without held data and Des returns a 500" in new Setup{

      when(mockHeldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(None))

      when(mockCTRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      when(mockCTRepository.updateSubmissionStatus(Matchers.any(), Matchers.any())).thenReturn(Future.successful("rejected"))

      when(mockAuditConnector.sendExtendedEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      when(mockDesConnector.topUpCTSubmission(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.failed(new InternalServerException("DES returned a 500")))

      when(mockCTRepository.removeTaxRegistrationById(Matchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockBRConnector.removeMetadata(Matchers.eq(validCR.registrationID))(Matchers.any()))
        .thenReturn(Future.successful(true))

      intercept[InternalServerException] {
        await(service.processIncorporationUpdate(incorpRejected))
      }

       }
  }

  "addressLine4Fix" should {

    val regId = "reg-12345"
    val addressLine4 = "testAL4"
    val encodedAddressLine4 = "dGVzdEFMNA=="
    val addressLine4Json = JsString(addressLine4)

    "amend a held submissions' address line 4 with the one provided through config if the reg id's match" in new SetupWithAddressLine4Fix(regId, encodedAddressLine4) {
      val result = await(service.addressLine4Fix(regId, heldJson))
      (result \ "registration" \ "corporationTax" \ "businessAddress" \ "line4").toOption shouldBe Some(addressLine4Json)
    }

    "do not amend a held submissions' address line 4 if the reg id's do not match" in new SetupWithAddressLine4Fix("otherRegID", encodedAddressLine4) {
      val result = await(service.addressLine4Fix(regId, heldJson))
      result shouldBe heldJson
    }
  }
}
