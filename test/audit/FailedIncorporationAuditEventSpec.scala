/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

class FailedIncorporationAuditEventSpec extends UnitSpec {

  implicit val hc = HeaderCarrier()
  implicit val format = Json.format[ExtendedDataEvent]

  val testModel = FailedIncorporationAuditEventDetail(
    "1234567890",
    "testReason"
  )

  "FailedIncorporationAuditEventDetail" should {
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
        result.getClass shouldBe classOf[JsObject]
        result shouldBe Json.parse(expected)
      }
    }
  }

  "FailedIncorporationAuditEvent" should {
    "construct a full successful incorporation audit event" when {
      "given a FailedIncorporationAuditEventDetail case class, an audit type and a transaction name" in {
        val auditEventTest = new FailedIncorporationAuditEvent(testModel, "failedIncorpInformation", "failedIncorpInformation")
        val result = Json.toJson[FailedIncorporationAuditEvent](auditEventTest)
        result.getClass shouldBe classOf[JsObject]
        (result \ "auditSource").as[String] shouldBe "company-registration"
        (result \ "auditType").as[String] shouldBe "failedIncorpInformation"

        val tagSet = (result \ "tags").as[JsObject]

        (tagSet \ "transactionName").as[String] shouldBe "failedIncorpInformation"
      }
    }
  }
}
