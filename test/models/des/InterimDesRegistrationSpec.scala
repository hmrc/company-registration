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

package models.des

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec


class InterimDesRegistrationSpec extends UnitSpec {

  "CompletionCapacity" should {
    "Construct a director" in { CompletionCapacity("Director") shouldBe Director }
    "Construct an agent" in { CompletionCapacity("Agent") shouldBe Agent }
    "Construct a direct from an other" in { CompletionCapacity("director") shouldBe Director }
    "Construct a agent from an other" in { CompletionCapacity("agent") shouldBe Agent }
    "Construct an other" in { CompletionCapacity("foo") shouldBe Other("foo") }
  }

  "Registration metadata model" should {

    "Simple model should produce valid JSON for a director" in {
      val expectedJson : String = s"""{
                               |  "businessType" : "Limited company",
                               |  "sessionId" : "session-123",
                               |  "credentialId" : "cred-123",
                               |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
                               |  "submissionFromAgent": false,
                               |  "language" : "ENG",
                               |  "completionCapacity" : "Director",
                               |  "declareAccurateAndComplete": true
                               |}""".stripMargin

      val desModel = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), CompletionCapacity(Director.text) )

      val result = Json.toJson[Metadata](desModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

    "Simple model should produce valid JSON for an agent" in {
      val expectedJson : String = s"""{
                               |  "businessType" : "Limited company",
                               |  "sessionId" : "session-123",
                               |  "credentialId" : "cred-123",
                               |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
                               |  "submissionFromAgent": false,
                               |  "language" : "ENG",
                               |  "completionCapacity" : "Agent",
                               |  "declareAccurateAndComplete": true
                               |}""".stripMargin

      val desModel = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), CompletionCapacity(Agent.text) )

      val result = Json.toJson[Metadata](desModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

    "Unexpected completion capacity should produce the other fields" in {
      val expectedJson : String = s"""{
                                      |  "businessType" : "Limited company",
                                      |  "sessionId" : "session-123",
                                      |  "credentialId" : "cred-123",
                                      |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
                                      |  "submissionFromAgent": false,
                                      |  "language" : "ENG",
                                      |  "completionCapacity" : "Other",
                                      |  "completionCapacityOther" : "wibble",
                                      |  "declareAccurateAndComplete": true
                                      |}""".stripMargin

      val desModel = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), CompletionCapacity("wibble") )

      val result = Json.toJson[Metadata](desModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

    "Putting Director in Other should return Director" in {
      val expectedJson : String = s"""{
                                      |  "businessType" : "Limited company",
                                      |  "sessionId" : "session-123",
                                      |  "credentialId" : "cred-123",
                                      |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
                                      |  "submissionFromAgent": false,
                                      |  "language" : "ENG",
                                      |  "completionCapacity" : "Director",
                                      |  "declareAccurateAndComplete": true
                                      |}""".stripMargin

      val desModel = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), CompletionCapacity("Director") )

      val result = Json.toJson[Metadata](desModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

  }

  "The Interim Registration corporationTax model" should {
    "Produce valid JSON for a fuller model" in {
      val expectedJson : String = s"""{
                                      |  "companyOfficeNumber" : "623",
                                      |  "hasCompanyTakenOverBusiness" : false,
                                      |  "companyMemberOfGroup" : false,
                                      |  "companiesHouseCompanyName" : "DG Limited",
                                      |  "returnsOnCT61" : false,
                                      |  "companyACharity" : false,
                                      |  "businessAddress" : {
                                      |                       "line1" : "1 Acacia Avenue",
                                      |                       "line2" : "Hollinswood",
                                      |                       "line3" : "Telford",
                                      |                       "line4" : "Shropshire",
                                      |                       "postcode" : "TF3 4ER",
                                      |                       "country" : "England"
                                      |                           },
                                      |  "businessContactName" : {
                                      |                           "firstName" : "Adam",
                                      |                           "middleNames" : "the",
                                      |                           "lastName" : "ant"
                                      |                           },
                                      |  "businessContactDetails" : {
                                      |                           "phoneNumber" : "0121 000 000",
                                      |                           "mobileNumber" : "0700 000 000",
                                      |                           "email" : "d@ddd.com"
                                      |                             }
                                      |}""".stripMargin
      val desBusinessAddress = BusinessAddress(
        "1 Acacia Avenue",
        "Hollinswood",
        Some("Telford"),
        Some("Shropshire"),
        Some("TF3 4ER"),
        Some("England")
      )

      val desBusinessContactName = BusinessContactName(
        "Adam",
        Some("the"),
        "ant"
      )
      val desBusinessContactContactDetails = BusinessContactDetails(
        Some("0121 000 000"),
        Some("0700 000 000"),
        Some("d@ddd.com")
      )

      val desModel = InterimCorporationTax(
                                  "DG Limited",
                                  false,
                                  Some(desBusinessAddress),
                                  desBusinessContactName,
                                  desBusinessContactContactDetails
                                )
      val result = Json.toJson[InterimCorporationTax](desModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

  }

  "The Interim Des Registration model" should {

    "Be able to be parsed into JSON" in {

      val expectedJson : String = s"""{  "acknowledgementReference" : "ackRef1",
                                      |  "registration" : {
                                      |  "metadata" : {
                                      |  "businessType" : "Limited company",
                                      |  "sessionId" : "session-123",
                                      |  "credentialId" : "cred-123",
                                      |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
                                      |  "submissionFromAgent": false,
                                      |  "language" : "ENG",
                                      |  "completionCapacity" : "Director",
                                      |  "declareAccurateAndComplete": true
                                      |  },
                                      |  "corporationTax" : {
                                      |  "companyOfficeNumber" : "623",
                                      |  "hasCompanyTakenOverBusiness" : false,
                                      |  "companyMemberOfGroup" : false,
                                      |  "companiesHouseCompanyName" : "DG Limited",
                                      |  "returnsOnCT61" : false,
                                      |  "companyACharity" : false,
                                      |  "businessAddress" : {
                                      |                       "line1" : "1 Acacia Avenue",
                                      |                       "line2" : "Hollinswood",
                                      |                       "line3" : "Telford",
                                      |                       "line4" : "Shropshire",
                                      |                       "postcode" : "TF3 4ER",
                                      |                       "country" : "England"
                                      |                           },
                                      |  "businessContactName" : {
                                      |                           "firstName" : "Adam",
                                      |                           "middleNames" : "the",
                                      |                           "lastName" : "ant"
                                      |                           },
                                      |  "businessContactDetails" : {
                                      |                           "phoneNumber" : "0121 000 000",
                                      |                           "mobileNumber" : "0700 000 000",
                                      |                           "email" : "d@ddd.com"
                                      |                             }
                                      |                             }
                                      |  }
                                      |}""".stripMargin

      val testMetadata = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), Director )
      val desBusinessAddress = BusinessAddress(
        "1 Acacia Avenue",
        "Hollinswood",
        Some("Telford"),
        Some("Shropshire"),
        Some("TF3 4ER"),
        Some("England")
      )

      val desBusinessContactName = BusinessContactName(
        "Adam",
        Some("the"),
        "ant"
      )
      val desBusinessContactContactDetails = BusinessContactDetails(
        Some("0121 000 000"),
        Some("0700 000 000"),
        Some("d@ddd.com")
      )

      val testInterimCorporationTax = InterimCorporationTax(
        "DG Limited",
        false,
        Some(desBusinessAddress),
        desBusinessContactName,
        desBusinessContactContactDetails
      )

      val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)

      val result = Json.toJson[InterimDesRegistration](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

    "should not parse empty strings" in {
      val expectedJson : String = s"""{  "acknowledgementReference" : "ackRef1",
                                      |  "registration" : {
                                      |  "metadata" : {
                                      |  "businessType" : "Limited company",
                                      |  "sessionId" : "session-123",
                                      |  "credentialId" : "cred-123",
                                      |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
                                      |  "submissionFromAgent": false,
                                      |  "language" : "ENG",
                                      |  "completionCapacity" : "Director",
                                      |  "declareAccurateAndComplete": true
                                      |  },
                                      |  "corporationTax" : {
                                      |  "companyOfficeNumber" : "623",
                                      |  "hasCompanyTakenOverBusiness" : false,
                                      |  "companyMemberOfGroup" : false,
                                      |  "companiesHouseCompanyName" : "DG Limited",
                                      |  "returnsOnCT61" : false,
                                      |  "companyACharity" : false,
                                      |  "businessContactName" : {
                                      |                           "firstName" : "Adam",
                                      |                           "middleNames" : "the",
                                      |                           "lastName" : "ant"
                                      |                           },
                                      |  "businessContactDetails" : {
                                      |                             "email" : "d@ddd.com"
                                      |                             }
                                      |                           }
                                      |  }
                                      |}""".stripMargin

      val testMetadata = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), Director )

      val desBusinessContactName = BusinessContactName(
        "Adam",
        Some("the"),
        "ant"
      )
      val desBusinessContactContactDetails = BusinessContactDetails(
        None,
        None,
        Some("d@ddd.com")
      )

      val testInterimCorporationTax = InterimCorporationTax(
        "DG Limited",
        false,
        None,
        desBusinessContactName,
        desBusinessContactContactDetails
      )

      val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)

      val result = Json.toJson[InterimDesRegistration](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }
  }
}
