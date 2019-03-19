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

import audit.{CTRegistrationSubmissionAuditEventDetails, DesResponse}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

class AuditServiceSpec extends UnitSpec with MockitoSugar {

  val mockAuditConnector = mock[AuditConnector]

  implicit val hc = HeaderCarrier()

  class Setup {
    object TestService extends AuditService {
      val auditConnector = mockAuditConnector
    }
  }

  "buildCTRegSubmissionEvent" should {
    "construct a successful AuditEvent" when {
      "given a details model that has processingDate and ackReg defined" in new Setup {
        val testModel = CTRegistrationSubmissionAuditEventDetails(
          "testJourneyId",
          Some("testProcessingDate"),
          Some("testAckRef"),
          None
        )

        val result = TestService.buildCTRegSubmissionEvent(testModel)

        result.auditSource shouldBe "company-registration"
        result.auditType shouldBe "ctRegistrationSubmissionSuccessful"
        result.tags("transactionName") shouldBe "CTRegistrationSubmission"
        result.detail.\("processingDate").as[JsValue] shouldBe Json.toJson("testProcessingDate")
        result.detail.\("acknowledgementReference").as[JsValue] shouldBe Json.toJson("testAckRef")
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

        result.auditSource shouldBe "company-registration"
        result.auditType shouldBe "ctRegistrationSubmissionFailed"
        result.tags("transactionName") shouldBe "CTRegistrationSubmissionFailed"
        result.detail.\("reason").as[JsValue] shouldBe Json.toJson("testReason")
      }
    }
  }

  "ctRegSubmissionFromJson" should {
    "construct a CTRegistrationSubmissionAuditEventDetails with ackRef and processingDate defined" when {
      "given a successful DES Response" in new Setup {
        val desResponse = Json.obj(
          "processingDate" -> "testDate",
          "acknowledgementReference" -> "testAckRef"
        )

        val result = TestService.ctRegSubmissionFromJson("testJourneyId", desResponse)

        result.processingDate shouldBe Some("testDate")
        result.acknowledgementReference shouldBe Some("testAckRef")
        result.reason shouldBe None
      }
    }

    "construct a CTRegistrationSubmissionAuditEventDetails with only the reason defined" when {
      "given a failed DES Response" in new Setup {
        val desResponse = Json.obj(
          "reason" -> "testReason"
        )

        val result = TestService.ctRegSubmissionFromJson("testJourneyId", desResponse)

        result.processingDate shouldBe None
        result.acknowledgementReference shouldBe None
        result.reason shouldBe Some("testReason")
      }
    }
  }
}
