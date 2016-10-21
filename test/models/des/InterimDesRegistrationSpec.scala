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

package models.des

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec


class InterimDesRegistrationSpec extends UnitSpec {

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

      val desModel = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), Director )

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

      val desModel = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), Agent )

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

      val desModel = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), Other("wibble") )

      val result = Json.toJson[Metadata](desModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

  }

  "The Interim Registration corporationTax model" should {
    "Produce valid JSON for a simple model" in {
      val expectedJson : String = s"""{
                                      |  "companyActiveDate" : "01-11-2016",
                                      |  "companiesHouseCompanyName" : "DG Limited",
                                      |  "crn" : "1234567890",
                                      |  "startDateOfFirstAccountingPeriod" : "01-11-2016",
                                      |  "intendedAccountsPreparationDate" : "01-11-2016",
                                      |  "returnsOnCT61" : "N",
                                      |  "businessAddress" : "business address model",
                                      |  "businessContactName" : {
                                      |                           "firstName" : "adam",
                                      |                           "middleNames" : "the",
                                      |                           "lastName" : "ant"
                                      |                           },
                                      |  "businessContactDetails" : {
                                      |                           "phoneNumber" : "0121 000 000",
                                      |                           "mobileNumber" : "0700 000 000",
                                      |                           "email" : "d@ddd.com"
                                      |                             }
                                      |}""".stripMargin
      val desModel = InterimCorporationTax(
                                  "01-11-2016",
                                  "DG Limited",
                                  "1234567890",
                                  "01-11-2016",
                                  "01-11-2016",
                                  "N",
                                  "business address model",
                                  BusinessContactName("adam",Some("the"),Some("ant")),
                                  BusinessContactDetails(Some("0121 000 000"),Some("0700 000 000"),Some("d@ddd.com"))
                                )
      val result = Json.toJson[InterimCorporationTax](desModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

  }

  "Des Registration model" should {
    "Be able to be parsed into JSON" in {

      val json1 : String = s"""{
           |  "acknowledgementReference" : "ackRef1",
           |  "wibble" : "xxx"
           |}""".stripMargin

      val testModel1 = InterimDesRegistration( "ackRef1" )

      val result = Json.toJson[InterimDesRegistration](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(json1)
    }

  }
}

