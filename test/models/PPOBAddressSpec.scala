/*
 * Copyright 2018 HM Revenue & Customs
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

import models.validation.APIValidation
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

  def j(line1: S = "1", line3: OS = None, line4: OS = None, pc: OS = None, country: OS = None, uprn: OS = None): S = {
    val extra: S = Seq(
      jsonLine("addressLine1", line1, false),
      jsonLine("addressLine3", line3, false),
      jsonLine("addressLine4", line4, false),
      jsonLine("postCode", pc, false),
      jsonLine("country", country, false),
      jsonLine("uprn", uprn, false)
    ).flatten.mkString(", ")
    s"""
       |{
       |  "houseNameNumber" : "hnn",
       |  "addressLine2" : "2",
       |  "addressLine4" : "4",
       |  "txid" : "txid",
       |  ${extra}
       |}
     """.stripMargin
  }

  "PPOBAddress Model - line 1" should {
    "Be able to be parsed from JSON" in {
      val line1 = "123456789012345678901234567"
      val json = j(line1=line1, pc=Some("ZZ1 1ZZ"))
      val expected = PPOBAddress(line1, "2", None, Some("4"), Some("ZZ1 1ZZ"), None, None, "txid")

      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))

      shouldBeSuccess(expected, result)
    }

    "fail to be read from JSON if is empty string" in {
      val json = j(line1="")
      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))

      shouldHaveErrors(result, JsPath() \ "addressLine1", Seq(ValidationError("error.minLength", 1)))
    }

    "be able parse to be read from JSON if line1 is longer than 27 characters returning 27 characters" in {
      val line1 = "1234567890123456789012345678"
      val json = j(line1="1234567890123456789012345678",pc=Some("ZZ1 1ZZ"))
      val expected = PPOBAddress(line1.take(27), "2", None, Some("4"), Some("ZZ1 1ZZ"), None, None, "txid")
      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))

      shouldBeSuccess(expected, result)
    }
  }

  "PPOBAddress Model - line 2" should {
    "Be able to be parsed from JSON" in {
      val line3 = Some("123456789012345678901234567")
      val json = j(line3=line3, country=Some("c"), uprn=Some("xxx"))
      val expected = PPOBAddress("1", "2", line3, Some("4"), None, Some("c"), Some("xxx"), "txid")

      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))

      shouldBeSuccess(expected, result)
    }

    "Be able to be parsed from JSON with no line3" in {
      val json = j(line3=None, country=Some("c"))
      val expected = PPOBAddress("1", "2", None, Some("4"), None, Some("c"), None, "txid")

      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))

      shouldBeSuccess(expected, result)
    }

    "Be able to be parsed from JSON - 18 chars in line 4" in {
      val line3 = Some("Line3")
      val addressLine4 = Some("18charsinaddLine4x")
      val json = j(line3=line3, line4=addressLine4, country=Some("c"), uprn=Some("xxx"))
      val expected = PPOBAddress("1", "2", line3, Some("18charsinaddLine4x"), None, Some("c"), Some("xxx"), "txid")
      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))
      shouldBeSuccess(expected, result)
    }

    "be able to parse if 19chars in Address Line 4, returning 18 characters" in {
      val line3 = Some("Line3")
      val line4 = Some("19charsinaddLine4xx")
      val json = j(line3=line3, line4=line4, country=Some("c"), uprn=Some("xxx"))
      val expected = PPOBAddress("1", "2", line3, Some("19charsinaddLine4x"), None, Some("c"), Some("xxx"), "txid")
      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))

      shouldBeSuccess(expected, result)
    }

    "fail to be read from JSON if line2 is empty string" in {
      val json = j(line3=Some(""), country=Some("c"))

      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))

      shouldHaveErrors(result, JsPath() \ "addressLine3", Seq(ValidationError("error.minLength", 1)))
    }

    "be able to parse from JSON if line2 is longer than 27 characters returning 27 characters" in {
      val line3 = Some("1234567890123456789012345678")
      val json = j(line3=line3, country=Some("c"))
      val expected = PPOBAddress("1", "2", line3.map(_.take(27)), Some("4"), None, Some("c"), None, "txid")
      val result = Json.parse(json).validate[PPOBAddress](PPOBAddress.normalisingReads(APIValidation))

      shouldBeSuccess(expected, result)
    }
  }

  "reading from json into a PPOBAddress case class" should {

    "return a PPOBAddress case class" in {
      val ppobAddress = PPOBAddress("1", "2", None, Some("4"), Some("ZZ1 1ZZ"), None, Some("xxx"), "txid")
      val json = Json.parse("""{"houseNameNumber":"hnn","addressLine1":"1","addressLine2":"2","addressLine4":"4","postCode":"ZZ1 1ZZ", "uprn": "xxx", "txid": "txid"}""")

      val result = Json.fromJson[PPOBAddress](json)(PPOBAddress.normalisingReads(APIValidation))
      result shouldBe JsSuccess(ppobAddress)
    }

    "throw a json validation error when a postcode and country do not exist" in {
      val json = Json.parse("""{"houseNameNumber":"hnn","addressLine1":"1","addressLine2":"2","addressLine4":"4", "txid": "txid"}""")

      val result = Json.fromJson[PPOBAddress](json)(PPOBAddress.normalisingReads(APIValidation))
      shouldHaveErrors(result, JsPath(), Seq(ValidationError("Must have at least one of postcode and country")))
    }
  }
}
