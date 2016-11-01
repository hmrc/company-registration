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

import connectors.IncorporationCheckAPIConnector
import models.{IncorpUpdate, SubmissionCheckResponse, SubmissionDates}
import org.joda.time.DateTime
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import repositories.StateDataRepository
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._

import scala.concurrent.Future

class RegistrationHoldingPenServiceSpec extends UnitSpec with MockitoSugar {

  val mockStateDataRepository = mock[StateDataRepository]
  val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]

  trait Setup {
    val service = new RegistrationHoldingPenService{
      val stateDataRepository = mockStateDataRepository
      val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
    }
  }

  val exampleDate = DateTime.parse("2012-12-12")
  val exampleDate1 = DateTime.parse("2020-5-10")
  val exampleDate2 = DateTime.parse("2025-6-6")

  "appendDataToSubmission" should {

    "be able to add final Json additions to the PartialSubmission" in new Setup {

      val crn = "012345"
      val dates = SubmissionDates(exampleDate, exampleDate1, exampleDate2)
      val submission: JsObject = Json.parse(s"""{  "acknowledgementReference" : "ackRef1",
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

      val expected = Json.parse(s"""{  "acknowledgementReference" : "ackRef1",
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

      val result = service.appendDataToSubmission(crn, dates, submission)

      result shouldBe expected
    }
  }

  "checkSubmission" should {

    val timepoint = "123456789"

    implicit val hc = HeaderCarrier()

    val submissionCheckResponse = SubmissionCheckResponse(
      Seq(
        IncorpUpdate(
          "transactionId",
          "status",
          "crn",
          "incorpDate",
          100000011)
      ),
      "testNextLink")

    "return a submission if a timepoint was retrieved successfully" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(Some(timepoint)))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponse))

      await(service.fetchSubmission) shouldBe submissionCheckResponse
    }

    "return a submission if a timepoint was not retrieved" in new Setup {
      when(mockStateDataRepository.retrieveTimePoint).thenReturn(Future.successful(None))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Matchers.eq(None))(Matchers.any()))
        .thenReturn(Future.successful(submissionCheckResponse))

      await(service.fetchSubmission) shouldBe submissionCheckResponse
    }
  }

  "formatDate" should {

    "format a DateTime timestamp into the format yyyy-mm-dd" in new Setup {
      val date = DateTime.parse("1970-01-01T00:00:00.000Z")
      service.formatDate(date) shouldBe "1970-01-01"
    }
  }
}
