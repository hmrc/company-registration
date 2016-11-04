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

import java.util.UUID

import connectors.{DesConnector, IncorporationCheckAPIConnector, InvalidDesRequest, SuccessDesResponse}
import fixtures.CorporationTaxRegistrationFixture
import models.{IncorpUpdate, SubmissionCheckResponse, SubmissionDates}
import org.joda.time.DateTime
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import repositories.{CorporationTaxRegistrationRepository, HeldSubmission, HeldSubmissionRepository, StateDataRepository}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._

import scala.concurrent.Future

class RegistrationHoldingPenServiceSpec extends UnitSpec with MockitoSugar with CorporationTaxRegistrationFixture {

  val mockStateDataRepository = mock[StateDataRepository]
  val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  val mockctRepository = mock[CorporationTaxRegistrationRepository]
  val mockheldRepo = mock[HeldSubmissionRepository]
  val mockAccountService = mock[AccountingDetailsService]
  val mockDesConnector = mock[DesConnector]

  trait mockService extends RegistrationHoldingPenService {
    val stateDataRepository = mockStateDataRepository
    val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
    val ctRepository = mockctRepository
    val heldRepo = mockheldRepo
    val accountingService = mockAccountService
    val desConnector = mockDesConnector
  }

  trait Setup {
    val service = new mockService {}
  }

  def date(year: Int, month: Int, day: Int) = new DateTime(year,month,day,0,0)
  def date(yyyyMMdd:String) = DateTime.parse(yyyyMMdd)

  val timepoint = "123456789"
  val testAckRef = UUID.randomUUID.toString
  val testRegId = UUID.randomUUID.toString
  val transId = UUID.randomUUID().toString
  val validCR = validHeldCTRegWithData(Some(testAckRef))
  val submissionCheckResponse = SubmissionCheckResponse(
    Seq(
      IncorpUpdate(
        transId,
        "status",
        "012345",
        new DateTime(2016, 8, 10, 0, 0),
        "100000011")
    ),
    "testNextLink")

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

  val crn = "012345"
  val exampleDate = "2012-12-12"
  val exampleDate1 = "2020-05-10"
  val exampleDate2 = "2025-06-06"
  val dates = SubmissionDates(date(exampleDate), date(exampleDate1), date(exampleDate2))

  val interimSubmission = Json.parse(sub(testAckRef)).as[JsObject]
  val validDesSubmission = Json.parse(sub(testAckRef,Some((crn,exampleDate,exampleDate1,exampleDate2)))).as[JsObject]

  val validHeld = HeldSubmission(testRegId, testAckRef, interimSubmission)

  "appendDataToSubmission" should {
    "be able to add final Json additions to the PartialSubmission" in new Setup {
      val result = service.appendDataToSubmission(crn, dates, interimSubmission)

      result shouldBe validDesSubmission
    }
  }

  "checkSubmission" should {
    "return a submission if a timepoint was retrieved successfully" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponse))

      Seq(await(service.fetchIncorpUpdate)) shouldBe submissionCheckResponse.items
    }

    "return a submission if a timepoint was not retrieved" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(None))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(None))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponse))

      Seq(await(service.fetchIncorpUpdate)) shouldBe submissionCheckResponse.items
    }
  }

  "formatDate" should {

    "format a DateTime timestamp into the format yyyy-mm-dd" in new Setup {
      val date = DateTime.parse("1970-01-01T00:00:00.000Z")
      service.formatDate(date) shouldBe "1970-01-01"
    }
  }

  "fetchHeldData" should {
    import RegistrationHoldingPenService.FailedToRetrieveByAckRef

    "return a valid Held record found" in new Setup {
      when(mockheldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      val result = service.fetchHeldData(testAckRef)
      await(result) shouldBe validHeld
    }
    "return a FailedToRetrieveByTxId when a record cannot be retrieved" in new Setup {
      when(mockheldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(None))

      val result = service.fetchHeldData(testAckRef)
      intercept[FailedToRetrieveByAckRef](await(result))
    }
  }

  "fetchRegistrationByTxIds" should {
    import RegistrationHoldingPenService.FailedToRetrieveByTxId

    "return a CorporationTaxRegistration document when one is found by Transaction ID" in new Setup{
      when(mockctRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      await(service.fetchRegistrationByTxId(transId)) shouldBe validCR

    }
    "return a FailedToRetrieveByTxId when one is not found" in new Setup {
      when(mockctRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(None))

      intercept[FailedToRetrieveByTxId](await(service.fetchRegistrationByTxId(transId)))
    }
  }

  "updateSubmission" should {
    "return a valid DES ready submission" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponse))

      when(mockctRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      when(mockheldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.ctSubmission(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(SuccessDesResponse))

      await(service.updateNextSubmissionByTimepoint) shouldBe validDesSubmission
    }

    "fail if DES states invalid" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponse))

      when(mockctRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      when(mockheldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(dates)

      when(mockDesConnector.ctSubmission(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(InvalidDesRequest("wibble")))

      intercept[InvalidSubmission] {
        await(service.updateNextSubmissionByTimepoint)
      }.message should endWith ("""- reason "wibble" """)
    }
  }

  "updateNextSubmissionByTimepoint" should {
    val expected = Json.obj("key" -> UUID.randomUUID().toString)
    trait SetupNoProcess {
      val service = new mockService {
        override def updateSubmission(item: IncorpUpdate) = Future.successful(expected)
      }
    }
    "return a Json.object for each incorp update" in new SetupNoProcess {

      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponse))

      val result = await(service.updateNextSubmissionByTimepoint)

      result shouldBe expected
    }
  }
}