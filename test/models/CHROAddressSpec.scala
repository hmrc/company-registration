/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsonValidationError, _}


class CHROAddressSpec extends WordSpec with Matchers with JsonFormatValidation {

  def lineEnd(comma: Boolean) = if (comma) "," else ""

  def jsonLine(key: String, value: String): String = jsonLine(key, value, true)

  def jsonLine(key: String, value: String, comma: Boolean): String = s""""${key}" : "${value}"${lineEnd(comma)}"""

  def jsonLine(key: String, value: Option[String], comma: Boolean = true): String = value.fold("")(v => s""""${key}" : "${v}"${lineEnd(comma)}""")

  def j(line1: String = "1", line2: Option[String] = None) = {
    s"""
       |{
       |  "premises" : "p",
       |  ${jsonLine("address_line_1", line1)}
       |  ${jsonLine("address_line_2", line2)}
       |  "country" : "c",
       |  "locality" : "l",
       |  "po_box" : "pb",
       |  "postal_code" : "pc",
       |  "region" : "r"
       |}
     """.stripMargin
  }

  "CHROAddress Model - line 1" should {
    "Be able to be parsed from JSON" in {
      val line1 = "12345678901234567890123456789012345678901234567890"
      val json = j(line1 = line1)
      val expected = CHROAddress("p", line1, None, "c", "l", Some("pb"), Some("pc"), Some("r"))

      val result = Json.parse(json).validate[CHROAddress]

      shouldBeSuccess(expected, result)
    }

    "fail to be read from JSON if is empty string" in {
      val json = j(line1 = "")

      val result = Json.parse(json).validate[CHROAddress]

      shouldHaveErrors(result, JsPath() \ "address_line_1", JsonValidationError("error.minLength", 1))
    }

    "fail to be read from JSON if line1 is longer than 50 characters" in {
      val json = j(line1 = "123456789012345678901234567890123456789012345678901")

      val result = Json.parse(json).validate[CHROAddress]

      shouldHaveErrors(result, JsPath() \ "address_line_1", JsonValidationError("error.maxLength", 50))
    }

  }

  "CHROAddress Model - line 2" should {
    "Be able to be parsed from JSON" in {
      val line2 = Some("12345678901234567890123456789012345678901234567890")
      val json = j(line2 = line2)
      val expected = CHROAddress("p", "1", line2, "c", "l", Some("pb"), Some("pc"), Some("r"))

      val result = Json.parse(json).validate[CHROAddress]

      shouldBeSuccess(expected, result)
    }

    "Be able to be parsed from JSON with no line2" in {
      val json = j(line2 = None)
      val expected = CHROAddress("p", "1", None, "c", "l", Some("pb"), Some("pc"), Some("r"))

      val result = Json.parse(json).validate[CHROAddress]

      shouldBeSuccess(expected, result)
    }

    "fail to be read from JSON if line2 is empty string" in {
      val json = j(line2 = Some(""))

      val result = Json.parse(json).validate[CHROAddress]

      shouldHaveErrors(result, JsPath() \ "address_line_2", JsonValidationError("error.minLength", 1))
    }

    "fail to be read from JSON if line2 is longer than 50 characters" in {
      val json = j(line2 = Some("123456789012345678901234567890123456789012345678901"))

      val result = Json.parse(json).validate[CHROAddress]

      shouldHaveErrors(result, JsPath() \ "address_line_2", JsonValidationError("error.maxLength", 50))
    }

  }
}
