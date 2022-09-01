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

package audit

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HeaderCarrier

class FailedIncorporationAuditEventSpec extends PlaySpec {

  implicit val hc = HeaderCarrier()

  val testModel = FailedIncorporationAuditEventDetail(
    "1234567890",
    "testReason"
  )

  "FailedIncorporationAuditEventDetail" must {
    "construct valid data as per confluence" when {
      "converting a case class to JSON for a successful incorp" in {
        val expected =
          """
            |{
            | "journeyId" : "1234567890",
            | "reason" : "testReason"
            |}
          """.stripMargin

        val result = Json.toJson[FailedIncorporationAuditEventDetail](testModel)
        result.getClass mustBe classOf[JsObject]
        result mustBe Json.parse(expected)
      }
    }
  }

  "FailedIncorporationAuditEvent" must {
    "construct a full successful incorporation audit event" when {
      "given a FailedIncorporationAuditEventDetail case class, an audit type and a transaction name" in {
        val auditEventTest = new FailedIncorporationAuditEvent(testModel, "failedIncorpInformation", "failedIncorpInformation")

        auditEventTest.auditSource mustBe "company-registration"
        auditEventTest.auditType mustBe "failedIncorpInformation"
        auditEventTest.tags.get("transactionName") mustBe Some("failedIncorpInformation")
      }
    }
  }
}
