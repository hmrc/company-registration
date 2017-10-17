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

package models

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec


class UserAccessSpec extends UnitSpec {

  "UserAccessModel" should {
    "With no email, be able to be parsed into JSON" in {

      val json : String =
        s"""
           |{
           |  "registration-id" : "regID",
           |  "created" : true,
           |  "confirmation-reference": false,
           |  "payment-reference": false
           |}
       """.stripMargin

      val testModel =
        UserAccessSuccessResponse(
          "regID",
          created= true,
          confRefs = false,
          paymentRefs = false
        )

      val result = Json.toJson[UserAccessSuccessResponse](testModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json)
    }

    "With email, be able to be parsed into JSON" in {

      val json : String =
        s"""
           |{
           |  "registration-id" : "regID",
           |  "created" : true,
           |  "confirmation-reference": false,
           |  "payment-reference": false,
           |  "email": { "address": "a@a.a", "type": "GG", "link-sent": true, "verified": false, "return-link-email-sent": false}
           |}
       """.stripMargin

      val testModel =
        UserAccessSuccessResponse(
          "regID",
          created= true,
          confRefs = false,
          paymentRefs = false,
          verifiedEmail = Some(Email("a@a.a", "GG", true, false, false))
        )

      val result = Json.toJson[UserAccessSuccessResponse](testModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json)
    }
  }
}
