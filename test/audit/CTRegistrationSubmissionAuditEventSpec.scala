/*
 * Copyright 2023 HM Revenue & Customs
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

package audit

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, Json}

class CTRegistrationSubmissionAuditEventSpec extends PlaySpec {

  "CTRegistrationSubmissionAuditEventSpec" must {
    "construct a valid details JSON structure as per confluence" when {
      "converting a case class to JSON for a successful submission" in {
        val expected: String =
          """
            |{
            |   "journeyId" : "testJourneyId",
            |   "processingDate" : "testDate",
            |   "acknowledgementReference" : "testAckRef"
            |}
          """.stripMargin

        val testModel =
          CTRegistrationSubmissionAuditEventDetails(
            "testJourneyId",
            Some("testDate"),
            Some("testAckRef"),
            None
          )

        val result = Json.toJson[CTRegistrationSubmissionAuditEventDetails](testModel)
        result.getClass mustBe classOf[JsObject]
        result mustBe Json.parse(expected)
      }

      "converting a case class to JSON for a failed submission" in {
        val expected: String =
          """
            |{
            |   "journeyId" : "testJourneyId",
            |   "reason" : "testReason"
            |}
          """.stripMargin

        val testModel =
          CTRegistrationSubmissionAuditEventDetails(
            "testJourneyId",
            None,
            None,
            Some("testReason")
          )

        val result = Json.toJson[CTRegistrationSubmissionAuditEventDetails](testModel)
        result.getClass mustBe classOf[JsObject]
        result mustBe Json.parse(expected)
      }
    }
  }
}
