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

import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable.WrappedArray

class PPOBAddressSpec extends UnitSpec with JsonFormatValidation {

  def lineEnd(comma: Boolean) = if( comma ) "," else ""
  def jsonLine(key: String, value: String): String = jsonLine(key, value, true)
  def jsonLine(key: String, value: String, comma: Boolean): String = s""""${key}" : "${value}"${lineEnd(comma)}"""
  def jsonLine(key: String, value: Option[String], comma: Boolean = true): String = value.fold("")(v=>s""""${key}" : "${v}"${lineEnd(comma)}""")

  def j(line1: String = "1", line3: Option[String] = None) = {
    s"""
       |{
       |  "houseNameNumber" : "hnn",
       |  ${jsonLine("addressLine1", line1)}
       |  "addressLine2" : "2",
       |  ${jsonLine("addressLine3", line3)}
       |  "addressLine4" : "4",
       |  "postCode" : "ZZ1 1ZZ",
       |  "country" : "c"
       |}
     """.stripMargin
  }

  "PPOBAddress Model - line 1" should {
    "Be able to be parsed from JSON" in {
      val line1 = "123456789012345678901234567"
      val json = j(line1=line1)
      val expected = PPOBAddress("hnn", line1, "2", None, Some("4"), Some("ZZ1 1ZZ"), Some("c") )

      val result = Json.parse(json).validate[PPOBAddress]

      shouldBeSuccess(expected, result)
    }

    "fail to be read from JSON if is empty string" in {
      val json = j(line1="")

      val result = Json.parse(json).validate[PPOBAddress]

      shouldHaveErrors(result, JsPath() \ "addressLine1", Seq(ValidationError("error.minLength", 1),ValidationError("error.pattern")))
    }

    "fail to be read from JSON if line1 is longer than 27 characters" in {
      val json = j(line1="1234567890123456789012345678")

      val result = Json.parse(json).validate[PPOBAddress]

      shouldHaveErrors(result, JsPath() \ "addressLine1", Seq(ValidationError("error.maxLength", 27),ValidationError("error.pattern")))
    }

  }

  "PPOBAddress Model - line 2" should {
    "Be able to be parsed from JSON" in {
      val line3 = Some("123456789012345678901234567")
      val json = j(line3=line3)
      val expected = PPOBAddress("hnn", "1", "2", line3, Some("4"), Some("ZZ1 1ZZ"), Some("c") )

      val result = Json.parse(json).validate[PPOBAddress]

      shouldBeSuccess(expected, result)
    }

    "Be able to be parsed from JSON with no line3" in {
      val json = j(line3=None)
      val expected = PPOBAddress("hnn", "1", "2", None, Some("4"), Some("ZZ1 1ZZ"), Some("c") )

      val result = Json.parse(json).validate[PPOBAddress]

      shouldBeSuccess(expected, result)
    }

    "fail to be read from JSON if line2 is empty string" in {
      val json = j(line3=Some(""))

      val result = Json.parse(json).validate[PPOBAddress]

      shouldHaveErrors(result, JsPath() \ "addressLine3", Seq(ValidationError("error.minLength", 1),ValidationError("error.pattern")))
    }

    "fail to be read from JSON if line2 is longer than 27 characters" in {
      val json = j(line3=Some("1234567890123456789012345678"))

      val result = Json.parse(json).validate[PPOBAddress]

      shouldHaveErrors(result, JsPath() \ "addressLine3", Seq(ValidationError("error.maxLength", 27),ValidationError("error.pattern")))
    }

  }
}
