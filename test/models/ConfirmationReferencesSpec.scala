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
import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec


class ConfirmationReferencesSpec extends UnitSpec with JsonFormatValidation {

  def j(ackRef: String) = {
    s"""
       |{
       |  "acknowledgement-reference" : "${ackRef}",
       |  "transaction-id" : "aaa",
       |  "payment-reference" : "bbb",
       |  "payment-amount" : "ccc"
       |}
     """.stripMargin
  }

  "Refactored ConfirmationReferences Model" should {
    "Be able to be parsed from JSON" in {
      val ackRef = "1234567890123456789012345678901"
      val json = j(ackRef)
      val expected = ConfirmationReferences(ackRef, "aaa", Some("bbb"), Some("ccc"))

      val result = Json.parse(json).validate[ConfirmationReferences]

      shouldBeSuccess(expected, result)
    }

    "fail to be read from JSON if the ack ref is longer than 31 characters" in {
      val json = j("12345678901234567890123456789012")

      val result = Json.parse(json).validate[ConfirmationReferences]

      shouldHaveErrors(result, JsPath() \ "acknowledgement-reference", ValidationError("error.maxLength", 31))
    }

  }
}
