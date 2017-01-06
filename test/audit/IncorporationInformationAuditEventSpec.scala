/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class IncorporationInformationAuditEventSpec extends UnitSpec {

  "IncorporationInformationAuditEvent" should {
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

        val result = Json.parse(expected).as[IncorporationInformationAuditEventDetail]
        result.journeyId shouldBe "1234567890"
        result.companyRegistrationNumber shouldBe Some("1234")
        result.reason shouldBe None
      }

      "converting a case class to JSON for an unsuccessful incorp" in {
        val expected =
          """
            |{
            | "journeyId" : "1234567890",
            | "reason" : "testReason"
            |}
          """.stripMargin

        val result = Json.parse(expected).as[IncorporationInformationAuditEventDetail]
        result.journeyId shouldBe "1234567890"
        result.companyRegistrationNumber shouldBe None
        result.reason shouldBe Some("testReason")
      }
    }
  }
}
