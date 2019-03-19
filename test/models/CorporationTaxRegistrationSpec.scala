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

import fixtures.CorporationTaxRegistrationFixture
import helpers.BaseSpec
import models.validation.APIValidation
import org.joda.time.{DateTime, DateTimeZone}
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class CorporationTaxRegistrationSpec extends BaseSpec with JsonFormatValidation with CorporationTaxRegistrationFixture {

  def now = DateTime.now(DateTimeZone.UTC)

  "CorporationTaxRegistration" should {

    val fullHeldJson = Json.parse(
      """
        |{
        | "internalId":"tiid",
        | "registrationID":"0123456789",
        | "status":"held",
        | "formCreationTimestamp":"2001-12-31T12:00:00Z",
        | "language":"en",
        | "confirmationReferences":{
        |  "acknowledgement-reference":"BRCT12345678910",
        |  "transaction-id":"TX1",
        |  "payment-reference":"PY1",
        |  "payment-amount":"12.00"
        | },
        | "accountingDetails":{
        |  "accountingDateStatus":"FUTURE_DATE",
        |  "startDateOfBusiness":"2019-12-31"
        | },
        | "createdTime":1485859623928
        |}
      """.stripMargin)

    "using a custom read on the held json document without a lastSignedIn value will default it to the current time" in {
      val before = now.getMillis
      val ct = Json.fromJson[CorporationTaxRegistration](fullHeldJson)(CorporationTaxRegistration.format(APIValidation, mockInstanceOfCrypto)).get
      val after = now.getMillis

      ct.lastSignedIn.getMillis >= before && ct.lastSignedIn.getMillis <= after shouldBe true
    }

    "using a custom read on the held json document without a lastSignedIn value will not change the rest of the document" in {
      val ct = Json.fromJson[CorporationTaxRegistration](fullHeldJson)(CorporationTaxRegistration.format(APIValidation, mockInstanceOfCrypto))
      validHeldCorporationTaxRegistration.copy(createdTime = ct.get.createdTime, lastSignedIn = ct.get.lastSignedIn) shouldBe ct.get
    }
  }

  "CompanyDetails Model - names" should {
        def tstJson(cName: String) = Json.parse(
            s"""
         |{
         |  "companyName":"$cName",
         |  "pPOBAddress": {
         |    "addressType":"MANUAL",
         |    "address": {
          |      "addressLine1":"15 St Walk",
         |      "addressLine2":"Testley",
         |      "addressLine3":"Testford",
         |      "addressLine4":"Testshire",
         |      "postCode": "ZZ1 1ZZ",
         |      "country":"UK",
         |      "txid":"txid"
         |    }
         |  },
         |   "cHROAddress": {
         |   "premises":"p",
         |    "address_line_1":"14 St Test Walk",
         |    "address_line_2":"Test",
         |    "country":"c",
         |    "locality":"l",
         |    "po_box":"pb",
         |    "postal_code":"TE1 1ST",
         |     "region" : "r"
         |  },
         |  "jurisdiction": "test"
         |}
        """.stripMargin)

          "fail on company name" when {
            "it is too long" in {
                val longName = List.fill(161)('a').mkString
                val json = tstJson(longName)

                  val result = Json.fromJson[CompanyDetails](json)
                  shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("Invalid company name")))
              }
            "it is too short" in {
                val json = tstJson("")

                  val result = Json.fromJson[CompanyDetails](json)

                  shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("Invalid company name")))
              }
            "it contains invalid character " in {
        val json = tstJson("étest|company")

                  val result = Json.fromJson[CompanyDetails](json)

                  shouldHaveErrors(result, JsPath() \ "companyName", Seq(ValidationError("Invalid company name")))
              }
          }

          "Be able to be parsed from JSON" when {

              "with valid company name" in {
                val chROAddress = CHROAddress("p","14 St Test Walk",Some("Test"),"c","l",Some("pb"),Some("TE1 1ST"),Some("r"))
                val ppobAddress = PPOBAddress("15 St Walk", "Testley", Some("Testford"), Some("Testshire"), Some("ZZ1 1ZZ"), Some("UK"), None, "txid")

                  val json = tstJson("ß Ǭscar ég ànt")
                val expected = CompanyDetails("ß Ǭscar ég ànt", chROAddress, PPOB(PPOB.MANUAL, Some(ppobAddress)), "test")

                  val result = Json.fromJson[CompanyDetails](json)

                  result shouldBe JsSuccess(expected)
              }
          }

       }
}
