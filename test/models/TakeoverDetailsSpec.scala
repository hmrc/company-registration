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

import assets.TestConstants.TakeoverDetails._
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsError, JsPath, JsSuccess, Json}

class TakeoverDetailsSpec extends WordSpec with Matchers {

  "takeover details" should {
    "return JsSuccess" when {
      "all fields are provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "replacingAnotherBusiness" -> true,
          "businessName" -> testBusinessName,
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersName" -> testPrevOwnerName,
          "prevOwnersAddress" -> testPrevOwnerAddress.toJson
        )

        val expected = JsSuccess(TakeoverDetails(
          replacingAnotherBusiness = true,
          businessName = Some(testBusinessName),
          businessTakeoverAddress = Some(testTakeoverAddressModel),
          prevOwnersName = Some(testPrevOwnerName),
          prevOwnersAddress = Some(testPrevOwnerAddressModel)
        ))

        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected
      }

      "previous owner name is not provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "replacingAnotherBusiness" -> true,
          "businessName" -> testBusinessName,
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersAddress" -> testPrevOwnerAddress.toJson
        )

        val expected = JsSuccess(TakeoverDetails(
          replacingAnotherBusiness = true,
          businessName = Some(testBusinessName),
          businessTakeoverAddress = Some(testTakeoverAddressModel),
          prevOwnersName = None,
          prevOwnersAddress = Some(testPrevOwnerAddressModel)
        ))

        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected
      }

      "previous owner address is not provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "replacingAnotherBusiness" -> true,
          "businessName" -> testBusinessName,
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersName" -> testPrevOwnerName
        )

        val expected = JsSuccess(TakeoverDetails(
          replacingAnotherBusiness = true,
          businessName = Some(testBusinessName),
          businessTakeoverAddress = Some(testTakeoverAddressModel),
          prevOwnersName = Some(testPrevOwnerName),
          prevOwnersAddress = None
        ))

        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected
      }

      "the business name has not been provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "replacingAnotherBusiness" -> true,
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersName" -> testPrevOwnerName,
          "prevOwnersAddress" -> testPrevOwnerAddress.toJson
        )

        val expected = JsSuccess(TakeoverDetails(
          replacingAnotherBusiness = true,
          businessName = None,
          businessTakeoverAddress = Some(testTakeoverAddressModel),
          prevOwnersName = Some(testPrevOwnerName),
          prevOwnersAddress = Some(testPrevOwnerAddressModel)
        ))
        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected
      }
    }

    "return a validation error" when {
      "replacingAnotherBusiness is not provided" in {
        val takeoverDetailsJson = Json.obj(fields =
          "businessName" -> testBusinessName,
          "businessTakeoverAddress" -> testTakeoverAddress.toJson,
          "prevOwnersName" -> testPrevOwnerName,
          "prevOwnersAddress" -> testPrevOwnerAddress.toJson
        )

        val expected = JsError(JsPath \ "replacingAnotherBusiness", "error.path.missing")

        val actual = Json.fromJson[TakeoverDetails](takeoverDetailsJson)

        actual shouldBe expected

      }
    }
  }

}
