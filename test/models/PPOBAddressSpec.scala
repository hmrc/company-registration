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

  type OS = Option[String]
  type S = String

  def lineEnd(comma: Boolean) = if( comma ) "," else ""
  def jsonLine(key: S, value: S): OS = jsonLine(key, value, true)
  def jsonLine(key: S, value: S, comma: Boolean): OS = Some(s""""${key}" : "${value}"${lineEnd(comma)}""")
  def jsonLine(key: S, value: OS, comma: Boolean = true): OS = value.map(v=>s""""${key}" : "${v}"${lineEnd(comma)}""")

  def j(line1: S = "1", line3: OS = None, pc: OS = None, country: OS = None): S = {
    val extra: S = Seq(
      jsonLine("addressLine1", line1, false),
      jsonLine("addressLine3", line3, false),
      jsonLine("postCode", pc, false),
      jsonLine("country", country, false)
    ).flatten.mkString(", ")
    s"""
       |{
       |  "houseNameNumber" : "hnn",
       |  "addressLine2" : "2",
       |  "addressLine4" : "4",
       |  ${extra}
       |}
     """.stripMargin
  }

  "PPOBAddress Model - line 1" should {
    "Be able to be parsed from JSON" in {
      val line1 = "123456789012345678901234567"
      val json = j(line1=line1, pc=Some("ZZ1 1ZZ"))
      val expected = PPOBAddress("hnn", line1, Some("2"), None, Some("4"), Some("ZZ1 1ZZ"), None )

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
      val json = j(line3=line3, country=Some("c"))
      val expected = PPOBAddress("hnn", "1", Some("2"), line3, Some("4"), None, Some("c") )

      val result = Json.parse(json).validate[PPOBAddress]

      shouldBeSuccess(expected, result)
    }

    "Be able to be parsed from JSON with no line3" in {
      val json = j(line3=None, country=Some("c"))
      val expected = PPOBAddress("hnn", "1", Some("2"), None, Some("4"), None, Some("c") )

      val result = Json.parse(json).validate[PPOBAddress]

      shouldBeSuccess(expected, result)
    }

    "fail to be read from JSON if line2 is empty string" in {
      val json = j(line3=Some(""), country=Some("c"))

      val result = Json.parse(json).validate[PPOBAddress]

      shouldHaveErrors(result, JsPath() \ "addressLine3", Seq(ValidationError("error.minLength", 1),ValidationError("error.pattern")))
    }

    "fail to be read from JSON if line2 is longer than 27 characters" in {
      val json = j(line3=Some("1234567890123456789012345678"), country=Some("c"))

      val result = Json.parse(json).validate[PPOBAddress]

      shouldHaveErrors(result, JsPath() \ "addressLine3", Seq(ValidationError("error.maxLength", 27),ValidationError("error.pattern")))
    }
  }

  "PPOBAddress Model - postcode & country" should {
    "Have at least one specified" in {
      val json = j(line3=None)

      intercept[IllegalArgumentException] {
        Json.parse(json).validate[PPOBAddress]
      }
    }
  }

}
