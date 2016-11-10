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

package models

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec


class UserAccessSpec extends UnitSpec {

  "UserAccessModel" should {
    "Be able to be parsed into JSON" in {

      val json1 : String =
        s"""
           |{
           |  "registration-id" : "regID",
           |  "created" : true,
           |  "confirmation-reference": false
           |}
       """.stripMargin

      val testModel1 =
        UserAccessSuccessResponse(
          "regID",
          created= true,
          confRefs = false
        )

      val result = Json.toJson[UserAccessSuccessResponse](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json1)
    }

  }
}
