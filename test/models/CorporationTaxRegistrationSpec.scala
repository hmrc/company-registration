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

import assets.TestConstants.CorporationTaxRegistration._
import assets.TestConstants.TakeoverDetails.{testTakeoverDetails, testTakeoverDetailsModel}
import fixtures.CorporationTaxRegistrationFixture
import helpers.BaseSpec
import models.validation.APIValidation
import org.joda.time.{DateTime, DateTimeZone}
import org.scalacheck.Prop.forAll
import org.scalacheck.{Gen, Prop, Test}
import play.api.libs.json.{JsonValidationError, _}

class CorporationTaxRegistrationSpec extends BaseSpec with JsonFormatValidation with CorporationTaxRegistrationFixture {

  def now = DateTime.now(DateTimeZone.UTC)

  "CorporationTaxRegistration" should {


    "using a custom read on the held json document without a lastSignedIn value will default it to the current time" in {
      val before = now.getMillis
      val fullHeldJson = fullCorpTaxRegJson(optAccountingDetails = Some(testAccountingDetails))
      val ct = Json.fromJson[CorporationTaxRegistration](fullHeldJson)(CorporationTaxRegistration.format(APIValidation, mockInstanceOfCrypto)).get
      val after = now.getMillis

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
      //TODO: Add assertion here
    }
  }

  "CompanyDetails Model - names" should {
    def testJson(companyName: String): JsObject = Json.obj(
      "companyName" -> companyName,
      "pPOBAddress" -> testPPOBAddress,
      "cHROAddress" -> testRegisteredOfficeAddress,
      "jurisdiction" -> "test"
    )

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

    "fail on company name" when {
      "it is too long" in {
        val longName = List.fill(161)('a').mkString
        val json = testJson(longName)
        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(JsonValidationError("Invalid company name")))
      }

      "it is too short" in {
        val emptyCompanyName = ""
        val json = testJson(emptyCompanyName)
        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(JsonValidationError("Invalid company name")))
      }

      "it contains invalid character " in {
        val invalidCompanyName = "étest|company"
        val json = testJson(invalidCompanyName)
        val result = Json.fromJson[CompanyDetails](json)
        shouldHaveErrors(result, JsPath() \ "companyName", Seq(JsonValidationError("Invalid company name")))
      }
    }

    "Be able to be parsed from JSON" when {
      "the company name is valid" in {
        val illegalCharacters: String = """[\u0000-\u001F\u007F~^``|]""" // The validator removes non ASCII chars but fails on these
        val arbitraryLegalUnfilteredChar: Gen[Char] = Gen.choose('\u001F', Char.MaxValue).filter(Character.isDefined)
        val validStrings = for {
          length <- Gen.choose(1, 156)
          collection <- Gen.containerOfN[Array, Char](length, arbitraryLegalUnfilteredChar)
        } yield collection.mkString

        val testRuns: Prop = forAll(validStrings) { name: String =>
          val filteredName: String = name.filterNot(illegalCharacters.contains(_)) // Scalacheck does not allow regex filtering so it is filtered after generation
          val json = testJson(s"test$filteredName")
          val result = Json.fromJson[CompanyDetails](json)

          val expected = CompanyDetails(s"test$filteredName", chROAddress, PPOB(PPOB.MANUAL, Some(ppobAddress)), "test")

          result == JsSuccess(expected)
        }

        Test.check(testRuns) {
          _.withMinSuccessfulTests(100000)
        }.succeeded shouldBe 100000
      }
    }
  }
}
