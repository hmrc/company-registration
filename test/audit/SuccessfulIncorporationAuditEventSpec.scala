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

import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HeaderCarrier

class SuccessfulIncorporationAuditEventSpec extends WordSpec with Matchers {

  implicit val hc = HeaderCarrier()

  val testModel = SuccessfulIncorporationAuditEventDetail(
    journeyId = "1234567890",
    companyRegistrationNumber = "1234",
    incorporationDate = DateTime.parse("2017-01-01")
  )

  "successfulIncorporationAuditEventDetail" should {
    "construct valid data as per confluence" when {
      "converting a case class to JSON for a successful incorp" in {
        val expected =
          """
            |{
            | "journeyId" : "1234567890",
            | "companyRegistrationNumber" : "1234",
            | "incorporationDate" : "2017-01-01"
            |}
          """.stripMargin

        val result = Json.toJson[SuccessfulIncorporationAuditEventDetail](testModel)
        result.getClass shouldBe classOf[JsObject]
        result shouldBe Json.parse(expected)
      }
    }
  }

  "SuccessfulIncorporationAuditEvent" should {
    "construct a full successful incorporation audit event" when {
      "given a SuccessfulIncorporationAuditEventDetail case class, an audit type and a transaction name" in {
        val auditEventTest = new SuccessfulIncorporationAuditEvent(testModel, "successIncorpInformation", "successIncorpInformation")

        auditEventTest.auditSource shouldBe "company-registration"
        auditEventTest.auditType shouldBe "successIncorpInformation"
        auditEventTest.tags.get("transactionName") shouldBe Some("successIncorpInformation")
      }
    }
  }
}
