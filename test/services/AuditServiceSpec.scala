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

package services

import audit.CTRegistrationSubmissionAuditEventDetails
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext

class AuditServiceSpec extends PlaySpec with MockitoSugar {

  val mockAuditConnector = mock[AuditConnector]

  implicit val hc = HeaderCarrier()

  class Setup {

    object TestService extends AuditService {
      val auditConnector = mockAuditConnector
      implicit val ec: ExecutionContext = global
    }

  }

  "buildCTRegSubmissionEvent" must {
    "construct a successful AuditEvent" when {
      "given a details model that has processingDate and ackReg defined" in new Setup {
        val testModel = CTRegistrationSubmissionAuditEventDetails(
          "testJourneyId",
          Some("testProcessingDate"),
          Some("testAckRef"),
          None
        )

        val result = TestService.buildCTRegSubmissionEvent(testModel)

        result.auditSource mustBe "company-registration"
        result.auditType mustBe "ctRegistrationSubmissionSuccessful"
        result.tags("transactionName") mustBe "CTRegistrationSubmission"
        result.detail.\("processingDate").as[JsValue] mustBe Json.toJson("testProcessingDate")
        result.detail.\("acknowledgementReference").as[JsValue] mustBe Json.toJson("testAckRef")
      }
    }

    "construct a failed AuditEvent" when {
      "given a details model that has reason defined" in new Setup {
        val testModel2 = CTRegistrationSubmissionAuditEventDetails(
          "testJourneyId",
          None,
          None,
          Some("testReason")
        )

        val result = TestService.buildCTRegSubmissionEvent(testModel2)

        result.auditSource mustBe "company-registration"
        result.auditType mustBe "ctRegistrationSubmissionFailed"
        result.tags("transactionName") mustBe "CTRegistrationSubmissionFailed"
        result.detail.\("reason").as[JsValue] mustBe Json.toJson("testReason")
      }
    }
  }

  "ctRegSubmissionFromJson" must {
    "construct a CTRegistrationSubmissionAuditEventDetails with ackRef and processingDate defined" when {
      "given a successful DES Response" in new Setup {
        val desResponse = Json.obj(
          "processingDate" -> "testDate",
          "acknowledgementReference" -> "testAckRef"
        )

        val result = TestService.ctRegSubmissionFromJson("testJourneyId", desResponse)

        result.processingDate mustBe Some("testDate")
        result.acknowledgementReference mustBe Some("testAckRef")
        result.reason mustBe None
      }
    }

    "construct a CTRegistrationSubmissionAuditEventDetails with only the reason defined" when {
      "given a failed DES Response" in new Setup {
        val desResponse = Json.obj(
          "reason" -> "testReason"
        )

        val result = TestService.ctRegSubmissionFromJson("testJourneyId", desResponse)

        result.processingDate mustBe None
        result.acknowledgementReference mustBe None
        result.reason mustBe Some("testReason")
      }
    }
  }
}
