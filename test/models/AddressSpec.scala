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

package models

import assets.TestConstants.TakeoverDetails._
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class AddressSpec extends UnitSpec {

  "address" should {
    "return JsSuccess" when {
      "all values have been provided" in {
        val addressJson = testTakeoverAddress.toJson
        val expected = JsSuccess(testTakeoverAddressModel)
        val actual = Json.fromJson[Address](addressJson)

        actual shouldBe expected
      }
      "lines 2, 3 and 4 of the  address are not provided" in {
        val addressJson = testTakeoverAddress.copy(optLine2 = None, optLine3 = None, optLine4 = None).toJson
        val expected = JsSuccess(testTakeoverAddressModel.copy(line2 = None, line3 = None, line4 = None))
        val actual = Json.fromJson[Address](addressJson)

        actual shouldBe expected
      }
      "postcode is not provided, but country is provided" in {
        val addressJson = testTakeoverAddress.copy(optPostcode = None).toJson
        val expected = JsSuccess(testTakeoverAddressModel.copy(postcode = None))
        val actual = Json.fromJson[Address](addressJson)

        actual shouldBe expected
      }
      "country is not provided, but postcode is provided" in {
        val addressJson = testTakeoverAddress.copy(optCountry = None).toJson
        val expected = JsSuccess(testTakeoverAddressModel.copy(country = None))
        val actual = Json.fromJson[Address](addressJson)

        actual shouldBe expected
      }
    }
    "return JsError" when {
      "the postcode and country have not been provided" in {
        val addressValidationMessage = "Must have at least one of postcode and country"
        val addressJson = testTakeoverAddress.copy(optPostcode = None, optCountry = None).toJson
        val expected = JsError(addressValidationMessage)
        val actual = Json.fromJson[Address](addressJson)

        actual shouldBe expected
      }
    }
  }
}
