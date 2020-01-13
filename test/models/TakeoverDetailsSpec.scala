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

package models

import play.api.libs.json.{JsError, JsPath, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec
import assets.TestConstants.TakeoverDetails._

class TakeoverDetailsSpec extends UnitSpec {

  "takeover details" should {
    "return JsSuccess" when {
      "all fields are provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "businessName" -> testBusinessName,
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersName" -> testPrevOwnerName,
          "prevOwnersAddress" -> testPrevOwnerAddress.toJson
        )

        val expected = JsSuccess(TakeoverDetails(
          businessName = testBusinessName,
          businessTakeoverAddress = Some(testTakeoverAddressModel),
          prevOwnersName = Some(testPrevOwnerName),
          prevOwnersAddress = Some(testPrevOwnerAddressModel)
        ))

        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected
      }
      "previous owner name is not provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "businessName" -> testBusinessName,
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersAddress" -> testPrevOwnerAddress.toJson
        )

        val expected = JsSuccess(TakeoverDetails(
          businessName = testBusinessName,
          businessTakeoverAddress = Some(testTakeoverAddressModel),
          prevOwnersName = None,
          prevOwnersAddress = Some(testPrevOwnerAddressModel)
        ))

        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected
      }
      "previous owner address is not provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "businessName" -> testBusinessName,
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersName" -> testPrevOwnerName
        )

        val expected = JsSuccess(TakeoverDetails(
          businessName = testBusinessName,
          businessTakeoverAddress = Some(testTakeoverAddressModel),
          prevOwnersName = Some(testPrevOwnerName),
          prevOwnersAddress = None
        ))

        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected
      }
    }
    "return a validation error" when {
      "the business name has not been provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersName" -> testPrevOwnerName,
          "prevOwnersAddress" -> testPrevOwnerAddress.toJson
        )

        val expected = JsError(JsPath \ "businessName", "error.path.missing")
        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected
      }
    }
  }

}
