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

import java.util.UUID

import auth.AuthClientConnector
import connectors._
import fixtures.CorporationTaxRegistrationFixture
import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, Json}
import repositories.CorporationTaxRegistrationRepository
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, InternalServerException}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}

import scala.concurrent.Future

class ProcessIncorporationServiceSpec extends UnitSpec with MockitoSugar with CorporationTaxRegistrationFixture with BeforeAndAfterEach with Eventually with LogCapturing {

  val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  val mockCTRepository = mock[CorporationTaxRegistrationRepository]
  val mockAccountService = mock[AccountingDetailsService]
  val mockDesConnector = mock[DesConnector]
  val mockBRConnector = mock[BusinessRegistrationConnector]
  val mockAuthConnector = mock[AuthConnector]
  val mockAuditConnector = mock[AuditConnector]
  val mockSendEmailService = mock[SendEmailService]

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = reset(
      mockAuthConnector,
      mockAuditConnector,
      mockIncorporationCheckAPIConnector,
      mockCTRepository,
      mockDesConnector,
      mockBRConnector,
      mockSendEmailService,
      mockAccountService
    )


  trait mockService extends ProcessIncorporationService {
    val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
    val ctRepository = mockCTRepository
    val accountingService = mockAccountService
    val desConnector = mockDesConnector
    val brConnector = mockBRConnector
    val auditConnector = mockAuditConnector
    val microserviceAuthConnector = mockAuthConnector
    val sendEmailService = mockSendEmailService
    override val addressLine4FixRegID: String = "false"
    override val amendedAddressLine4: String = ""
    override val blockageLoggingDay : String = "MON,TUE,WED,THU,FRI"
    override val blockageLoggingTime : String = "08:00:00_17:00:00"

    override def inWorkingHours: Boolean = true
  }

  trait Setup {
    val service = new mockService {}
  }

  class SetupWithAddressLine4Fix(regId: String, addressLine4: String) {
    val service = new mockService {
      override val addressLine4FixRegID: String = regId
      override val amendedAddressLine4: String = addressLine4
    }
  }

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
  val acknowledgedCR = validCR.copy(status = ACKNOWLEDGED)
  val failCaseCR = validCR.copy(status = DRAFT)
  val incorpSuccess = IncorpUpdate(transId, "accepted", Some("012345"), Some(new DateTime(2016, 8, 10, 0, 0)), timepoint)
  val incorpRejected = IncorpUpdate(transId, "rejected", None, None, timepoint, Some("testReason"))
  val submissionCheckResponseSingle = SubmissionCheckResponse(Seq(incorpSuccess), "testNextLink")
  val submissionCheckResponseDouble = SubmissionCheckResponse(Seq(incorpSuccess,incorpSuccess), "testNextLink")
  val submissionCheckResponseNone = SubmissionCheckResponse(Seq(), "testNextLink")

  def sub(a: String, others: Option[(String, String, String, String)] = None) = {
    val extra = others match {
      case None => ""
      case Some((crn, active, firstPrep, intended)) =>
        s"""
           |  ,
           |  "crn" : "$crn",
           |  "companyActiveDate": "$active",
           |  "startDateOfFirstAccountingPeriod": "$firstPrep",
           |  "intendedAccountsPreparationDate": "$intended"
           |""".stripMargin
    }

    s"""{  "acknowledgementReference" : "$a",
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
        |  "businessContactDetails" : {"phoneNumber" : "0123457889","mobileNumber" : "07654321000","email" : "foo@bar.com"}
        |  $extra
        |  }
        |  }
        |}""".stripMargin
  }

  def topUpSub(s: String, a:String, crn:String, active:String, firstPrep:String, intended:String) =
        s"""{
           |  "status" : "$s",
           |  "acknowledgementReference" : "$a",
           |  "corporationTax" : {
           |  "crn" : "$crn",
           |  "companyActiveDate": "$active",
           |  "startDateOfFirstAccountingPeriod": "$firstPrep",
           |  "intendedAccountsPreparationDate": "$intended"
           |  }
           |  }
           |""".stripMargin


  def topUpRejSub(s: String, a:String) =
    s"""{
        |  "status" : "$s",
        |  "acknowledgementReference" : "$a"
        |  }
        |""".stripMargin


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

  "formatDate" should {
    "format a DateTime timestamp into the format yyyy-mm-dd" in new Setup {
      val date = DateTime.parse("1970-01-01T00:00:00.000Z")
      service.formatDate(date) shouldBe "1970-01-01"
    }
  }

  "deleteRejectedSubmissionData" should {
    "return true if both br and ct data have been removed" in new Setup {
      when(mockCTRepository.removeTaxRegistrationById(ArgumentMatchers.eq(testRegId)))
        .thenReturn(Future.successful(true))
      when(mockBRConnector.removeMetadata(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      await(service.deleteRejectedSubmissionData(testRegId)) shouldBe true
    }

    "return an exception if ct data has not been fully removed" in new Setup {
      when(mockCTRepository.removeTaxRegistrationById(ArgumentMatchers.eq(testRegId)))
        .thenReturn(Future.successful(false))
      when(mockBRConnector.removeMetadata(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      intercept[FailedToDeleteSubmissionData.type](await(service.deleteRejectedSubmissionData(testRegId)))
    }

    "return an exception if br data has not been fully removed" in new Setup {
      when(mockCTRepository.removeTaxRegistrationById(ArgumentMatchers.eq(testRegId)))
        .thenReturn(Future.successful(true))
      when(mockBRConnector.removeMetadata(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      intercept[FailedToDeleteSubmissionData.type](await(service.deleteRejectedSubmissionData(testRegId)))
    }

    "return an exception if an exception occurs while removing ct data" in new Setup {
      when(mockCTRepository.removeTaxRegistrationById(ArgumentMatchers.eq(testRegId)))
        .thenReturn(Future.failed(new Exception))
      when(mockBRConnector.removeMetadata(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(false))

      intercept[Exception](await(service.deleteRejectedSubmissionData(testRegId)))
    }

    "return an exception if an exception occurs while removing br data" in new Setup {
      when(mockCTRepository.removeTaxRegistrationById(ArgumentMatchers.eq(testRegId)))
        .thenReturn(Future.successful(true))
      when(mockBRConnector.removeMetadata(ArgumentMatchers.eq(testRegId))(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception))

      intercept[Exception](await(service.deleteRejectedSubmissionData(testRegId)))
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
      when(mockAccountService.calculateSubmissionDates(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(dates)

      val result = service.calculateDates(incorpSuccess, validCR.accountingDetails, validCR.accountsPreparation)

      await(result) shouldBe dates
    }
  }

  "updateSubmission" should {
    trait SetupNoProcess {
      val service = new mockService {
        implicit val hc = new HeaderCarrier()

        override def updateHeldSubmission(item: IncorpUpdate, ctReg: CorporationTaxRegistration, journeyId : String, isAdmin: Boolean = false)(implicit hc : HeaderCarrier) = Future.successful(true)
      }
    }
    "return true for a DES ready submission" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      await(service.updateSubmissionWithIncorporation(incorpSuccess, validCR)) shouldBe true
    }
    "return false for a Locked registration" in new SetupNoProcess {
      val lockedReg = validCR.copy(status = LOCKED)
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(lockedReg)))

      await(service.updateSubmissionWithIncorporation(incorpSuccess, lockedReg)) shouldBe false
    }
    "return true for a submission that is already Submitted" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(submittedCR)))

      await(service.updateSubmissionWithIncorporation(incorpSuccess, submittedCR)) shouldBe true
    }
    "return true for a submission that is already Acknowledged" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(acknowledgedCR)))

      await(service.updateSubmissionWithIncorporation(incorpSuccess, submittedCR)) shouldBe true
    }
    "throw UnexpectedStatus for a submission that is neither 'Held' nor 'Submitted'" in new SetupNoProcess {
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(failCaseCR)))

      intercept[UnexpectedStatus]{await(service.updateSubmissionWithIncorporation(incorpSuccess, failCaseCR))}
    }

  }

  "processIncorporationUpdate" should {

    class SetupBoolean(boole: Boolean) {
      val service = new mockService {
        override def updateSubmissionWithIncorporation(item: IncorpUpdate, ctReg: CorporationTaxRegistration, isAdmin: Boolean = false)(implicit hc : HeaderCarrier) = Future.successful(boole)
      }
    }

    "return a future true when processing an accepted incorporation" in new SetupBoolean(true) {
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))
      when(mockSendEmailService.sendVATEmail(ArgumentMatchers.eq("testemail.com"), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]())).thenReturn(Future.successful(true))
      await(service.processIncorporationUpdate(incorpSuccess)) shouldBe true
    }

    "return a future true when processing an accepted incorporation and the email fails to send" in new SetupBoolean(true) {
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))
      when(mockSendEmailService.sendVATEmail(ArgumentMatchers.eq("testemail.com"), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.failed(new EmailErrorResponse("503")))

      await(service.processIncorporationUpdate(incorpSuccess)) shouldBe true
    }

    "return a future false when processing an accepted incorporation returns a false" in new SetupBoolean(false) {
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))
      await(service.processIncorporationUpdate(incorpSuccess)) shouldBe false
    }

    "return a future true when processing a rejected incorporation" in new Setup{
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      when(mockCTRepository.updateSubmissionStatus(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful("rejected"))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))

      when(mockDesConnector.topUpCTSubmission(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      when(mockCTRepository.removeTaxRegistrationById(ArgumentMatchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockBRConnector.removeMetadata(ArgumentMatchers.eq(validCR.registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockCTRepository.removeUnnecessaryRegistrationInformation(ArgumentMatchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      await(service.processIncorporationUpdate(incorpRejected)) shouldBe true
    }

    "return a future false, do not top up on rejected incorporation in LOCKED state" in new Setup {
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR.copy(status = LOCKED))))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))

      await(service.processIncorporationUpdate(incorpRejected)) shouldBe false
    }

    "return an exception when processing a rejected incorporation and Des returns a 500" in new Setup{
      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))
      when(mockCTRepository.updateSubmissionStatus(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful("rejected"))
      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))
      when(mockDesConnector.topUpCTSubmission(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new InternalServerException("DES returned a 500")))
      when(mockCTRepository.removeTaxRegistrationById(ArgumentMatchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))
      when(mockBRConnector.removeMetadata(ArgumentMatchers.eq(validCR.registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      intercept[InternalServerException] {
        await(service.processIncorporationUpdate(incorpRejected))
      }
    }

    "log a pagerduty if no reg document is found" in new Setup{

      when(mockCTRepository.retrieveRegistrationByTransactionID(ArgumentMatchers.eq(transId)))
        .thenReturn(Future.successful(None))

      when(mockCTRepository.updateSubmissionStatus(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful("rejected"))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))

      when(mockCTRepository.removeTaxRegistrationById(ArgumentMatchers.eq(validCR.registrationID)))
        .thenReturn(Future.successful(true))

      when(mockBRConnector.removeMetadata(ArgumentMatchers.eq(validCR.registrationID))(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        await(service.processIncorporationUpdate(incorpSuccess)) shouldBe true

        eventually {
          logEvents.size shouldBe 2
          val res = logEvents.map(_.getMessage) contains "CT_ACCEPTED_NO_REG_DOC_II_SUBS_DELETED"

          res shouldBe true
        }
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
