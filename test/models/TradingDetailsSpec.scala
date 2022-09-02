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

package models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsonValidationError
import play.api.libs.json.{JsPath, Json}

class TradingDetailsSpec extends PlaySpec {

  "Reading from Json with regular payments string" must {

    "succeed when a trading details model contains a 'true' is supplied as json" in {
      val json = Json.parse("""{"regularPayments":"true"}""")
      val result = json.validate[TradingDetails]

      result.isSuccess mustBe true
      result.asOpt.get mustBe TradingDetails("true")
    }

    "succeed when a trading details model contains a 'false' is supplied as json" in {
      val json = Json.parse("""{"regularPayments":"false"}""")
      val result = json.validate[TradingDetails]

      result.isSuccess mustBe true
      result.asOpt.get mustBe TradingDetails("false")
    }

    "fail validation when Trading details does not contain a 'true' or a 'false'" in {
      val json = Json.parse("""{"regularPayments":"test"}""")
      val result = json.validate[TradingDetails]

      result.isSuccess mustBe false
      result.asEither.left.get mustBe Seq((JsPath \ "regularPayments", List(JsonValidationError("expected either 'true' or 'false' but neither was found"))))
    }
  }

  "Reading from Json with regular payments boolean" must {

    "succeed when a trading details model contains a 'true' is supplied as json" in {
      val json = Json.parse("""{"regularPayments":true}""")
      val result = json.validate[TradingDetails]

      result.isSuccess mustBe true
      result.asOpt.get mustBe TradingDetails("true")
    }

    "succeed when a trading details model contains a 'false' is supplied as json" in {
      val json = Json.parse("""{"regularPayments":false}""")
      val result = json.validate[TradingDetails]

      result.isSuccess mustBe true
      result.asOpt.get mustBe TradingDetails("false")
    }
  }
}
