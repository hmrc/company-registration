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

import connectors.IncorporationCheckAPIConnector
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

  trait Setup {
    val service = new RegistrationHoldingPenService {
      val stateDataRepository = mockStateDataRepository
      val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
      val ctRepository = mockctRepository
      val heldRepo = mockheldRepo
      val accountingService = mockAccountService
    }
  }
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
  val exampleDate = DateTime.parse("2012-12-12")

  val exampleDate1 = DateTime.parse("2020-5-10")
  val exampleDate2 = DateTime.parse("2025-6-6")
  val submission: JsObject = Json.parse(s"""{  "acknowledgementReference" : "${testAckRef}",
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
                                            |  "companiesHouseCompanyName" : "DG Limited",
                                            |  "returnsOnCT61" : false,
                                            |  "companyACharity" : false,
                                            |  "businessAddress" : {
                                            |                       "line1" : "1 Acacia Avenue",
                                            |                       "line2" : "Hollinswood",
                                            |                       "line3" : "Telford",
                                            |                       "line4" : "Shropshire",
                                            |                       "postcode" : "TF3 4ER",
                                            |                       "country" : "England"
                                            |                           },
                                            |  "businessContactName" : {
                                            |                           "firstName" : "Adam",
                                            |                           "middleNames" : "the",
                                            |                           "lastName" : "ant"
                                            |                           },
                                            |  "businessContactDetails" : {
                                            |                           "phoneNumber" : "0121 000 000",
                                            |                           "mobileNumber" : "0700 000 000",
                                            |                           "email" : "d@ddd.com"
                                            |                             }
                                            |                             }
                                            |  }
                                            |}""".stripMargin).as[JsObject]

  val validDesSubmission = Json.parse(s"""{  "acknowledgementReference" : "${testAckRef}",
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
                                          |  "companiesHouseCompanyName" : "DG Limited",
                                          |  "returnsOnCT61" : false,
                                          |  "companyACharity" : false,
                                          |  "businessAddress" : {
                                          |                       "line1" : "1 Acacia Avenue",
                                          |                       "line2" : "Hollinswood",
                                          |                       "line3" : "Telford",
                                          |                       "line4" : "Shropshire",
                                          |                       "postcode" : "TF3 4ER",
                                          |                       "country" : "England"
                                          |                           },
                                          |  "businessContactName" : {
                                          |                           "firstName" : "Adam",
                                          |                           "middleNames" : "the",
                                          |                           "lastName" : "ant"
                                          |                           },
                                          |  "businessContactDetails" : {
                                          |                           "phoneNumber" : "0121 000 000",
                                          |                           "mobileNumber" : "0700 000 000",
                                          |                           "email" : "d@ddd.com"
                                          |                             },
                                          |  "crn" : "012345",
                                          |  "companyActiveDate": "2012-12-12",
                                          |  "startDateOfFirstAccountingPeriod": "2020-05-10",
                                          |  "intendedAccountsPreparationDate": "2025-06-06"
                                          |  }
                                          |  }
                                          |}""".stripMargin).as[JsObject]

  val validHeld = HeldSubmission(testRegId, testAckRef, submission)

  "appendDataToSubmission" should {

    "be able to add final Json additions to the PartialSubmission" in new Setup {

      val crn = "012345"
      val dates = SubmissionDates(exampleDate, exampleDate1, exampleDate2)

      val result = service.appendDataToSubmission(crn, dates, submission)

      result shouldBe validDesSubmission
    }
  }

  "checkSubmission" should {

    implicit val hc = HeaderCarrier()



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

      val result = service.fetchHeldData(Some(testAckRef))
      await(result) shouldBe validHeld
    }
    "return a FailedToRetrieveByTxId when a record cannot be retrieved" in new Setup {
      when(mockheldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(None))

      val result = service.fetchHeldData(Some(testAckRef))
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

  def date(year: Int, month: Int, day: Int) = new DateTime(year,month,day,0,0)

  "updateSubmission" should {
    implicit val hc = HeaderCarrier()
    "return a valid DES ready submission" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))

      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponse))

      when(mockctRepository.retrieveRegistrationByTransactionID(Matchers.eq(transId)))
        .thenReturn(Future.successful(Some(validCR)))

      when(mockheldRepo.retrieveSubmissionByAckRef(Matchers.eq(testAckRef)))
        .thenReturn(Future.successful(Some(validHeld)))

      when(mockAccountService.calculateSubmissionDates(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(SubmissionDates(date(2012,12,12), date(2020,5,10), date(2025,6,6)))

      await(service.updateSubmission) shouldBe validDesSubmission
    }
  }

}
