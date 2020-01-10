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

import assets.TestConstants.CorporationTaxRegistration._
import assets.TestConstants.TakeoverDetails.{testTakeoverDetails, testTakeoverDetailsModel}
import fixtures.CorporationTaxRegistrationFixture
import helpers.BaseSpec
import models.validation.APIValidation
import org.joda.time.{DateTime, DateTimeZone}
import play.api.data.validation.ValidationError
import play.api.libs.json._

class CorporationTaxRegistrationSpec extends BaseSpec with JsonFormatValidation with CorporationTaxRegistrationFixture {

  def now = DateTime.now(DateTimeZone.UTC)

  "CorporationTaxRegistration" should {



    "using a custom read on the held json document without a lastSignedIn value will default it to the current time" in {
      val before = now.getMillis
      val fullHeldJson = fullCorpTaxRegJson(optAccountingDetails = Some(testAccountingDetails))
      val ct = Json.fromJson[CorporationTaxRegistration](fullHeldJson)(CorporationTaxRegistration.format(APIValidation, mockInstanceOfCrypto)).get
      val after = now.getMillis

      println(s"\n\nlastSignedIn: ${ct.lastSignedIn.getMillis}\nBefore: $before\nAfter: $after")

      ct.lastSignedIn.getMillis >= before && ct.lastSignedIn.getMillis <= after shouldBe true
    }
    "using a custom read on the held json document without a lastSignedIn value will not change the rest of the document" in {
      val fullHeldJson = fullCorpTaxRegJson(optAccountingDetails = Some(testAccountingDetails))
      val ct = Json.fromJson[CorporationTaxRegistration](fullHeldJson)(CorporationTaxRegistration.format(APIValidation, mockInstanceOfCrypto))
      validHeldCorporationTaxRegistration.copy(
        createdTime = ct.get.createdTime,
        lastSignedIn = ct.get.lastSignedIn
      ) shouldBe ct.get
    }
    "parse the takeover details section" in {
      val testDateTime = DateTime.now(DateTimeZone.UTC)
      val ctrJson = fullCorpTaxRegJson(optAccountingDetails = Some(testAccountingDetails), optTakeoverDetails = Some(testTakeoverDetails))
      val expected = validHeldCorporationTaxRegistration.copy(
        createdTime = testDateTime,
        lastSignedIn = testDateTime,
        takeoverDetails = Some(testTakeoverDetailsModel)
      )
    }
  }

  "CompanyDetails Model - names" should {
    def testJson(companyName: String): JsObject = Json.obj(
      "companyName" -> companyName,
      "pPOBAddress" -> testPPOBAddress,
      "cHROAddress" -> testRegisteredOfficeAddress,
      "jurisdiction" -> "test"
    )

    "fail on company name" when {
      "it is too long" in {
        val longName = List.fill(161)('a').mkString
        val json = testJson(longName)
        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("Invalid company name")))
      }
      "it is too short" in {
        val emptyCompanyName = ""
        val json = testJson(emptyCompanyName)
        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("Invalid company name")))
      }
      "it contains invalid character " in {
        val invalidCompanyName = "étest|company"
        val json = testJson(invalidCompanyName)
        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("Invalid company name")))
      }
    }

    "Be able to be parsed from JSON" when {
      "with valid company name" in {
        val chROAddress = CHROAddress(
          premises = testPremises,
          address_line_1 = testRegOffLine1,
          address_line_2 = Some(testRegOffLine2),
          country = testRegOffCountry,
          locality = testRegOffLocality,
          po_box = Some(testRegOffPoBox),
          postal_code = Some(testRegOffPostcode),
          region = Some(testRegOffRegion)
        )

        val ppobAddress = PPOBAddress(
          line1 = testPPOBLine1,
          line2 = testPPOBLine2,
          line3 = Some(testPPOBLine3),
          line4 = Some(testPPOBLine4),
          postcode = Some(testPPOBPostcode),
          country = Some(testPPOBCountry),
          uprn = None,
          txid = testTransactionId
        )
        val json = testJson("ß Ǭscar ég ànt")
        val expected = CompanyDetails("ß Ǭscar ég ànt", chROAddress, PPOB(PPOB.MANUAL, Some(ppobAddress)), "test")
        val result = Json.fromJson[CompanyDetails](json)

        result shouldBe JsSuccess(expected)
      }
    }

  }
}
