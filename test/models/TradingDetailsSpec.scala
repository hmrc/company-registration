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

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, Json}
import uk.gov.hmrc.play.test.UnitSpec

class TradingDetailsSpec extends UnitSpec {

  "Reading from Json" should {

    "succeed when a trading details model contains a 'true' is supplied as json" in {
      val json = Json.toJson(TradingDetails("true"))
      val result = json.validate[TradingDetails]

      result.isSuccess shouldBe true
    }

    "succeed when a trading details model contains a 'false' is supplied as json" in {
      val json = Json.toJson(TradingDetails("false"))
      val result = json.validate[TradingDetails]

      result.isSuccess shouldBe true
    }

    "fail validation when Trading details does not contain a 'true' or a 'false'" in {
      val json = Json.toJson(TradingDetails("test"))
      val result = json.validate[TradingDetails]

      result.isSuccess shouldBe false
      result.asEither.left.get shouldBe Seq((JsPath \ "regularPayments",List(ValidationError("expected either 'true' or 'false' but neither was found"))))
    }
  }
}
