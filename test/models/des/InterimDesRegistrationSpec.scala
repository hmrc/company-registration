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

package models.des

import models._
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsObject, Json}


class InterimDesRegistrationSpec extends WordSpec with Matchers {

  "CompletionCapacity" should {
    "Construct a director" in { CompletionCapacity("Director") shouldBe Director }
    "Construct an agent" in { CompletionCapacity("Agent") shouldBe Agent }
    "Construct a secretary" in { CompletionCapacity("Company secretary") shouldBe Secretary }
    "Construct a direct from an other" in { CompletionCapacity("director") shouldBe Director }
    "Construct an agent from an other" in { CompletionCapacity("agent") shouldBe Agent }
    "Construct a secretary from an other" in { CompletionCapacity("company secretary") shouldBe Secretary }
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

    "Simple model should produce valid JSON for an secretary" in {
      val expectedJson : String = s"""{
                                     |  "businessType" : "Limited company",
                                     |  "sessionId" : "session-123",
                                     |  "credentialId" : "cred-123",
                                     |  "formCreationTimestamp": "1970-01-01T00:00:00.000Z",
                                     |  "submissionFromAgent": false,
                                     |  "language" : "ENG",
                                     |  "completionCapacity" : "Company secretary",
                                     |  "declareAccurateAndComplete": true
                                     |}""".stripMargin

      val desModel = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), CompletionCapacity(Secretary.text) )

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

      val desBusinessContactContactDetails = BusinessContactDetails(
        Some("0121 000 000"),
        Some("0700 000 000"),
        Some("d@ddd.com")
      )

      val desModel = InterimCorporationTax(
                                  "DG Limited",
                                  false,
                                  Some(desBusinessAddress),
                                  desBusinessContactContactDetails
                                )
      val result = Json.toJson[InterimCorporationTax](desModel)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

  }

  "The Interim Des Registration model" should {

    "Be able to be parsed into JSON when groups is NONE" in {

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

      val desBusinessContactContactDetails = BusinessContactDetails(
        Some("0121 000 000"),
        Some("0700 000 000"),
        Some("d@ddd.com")
      )

      val testInterimCorporationTax = InterimCorporationTax(
        "DG Limited",
        false,
        Some(desBusinessAddress),
        desBusinessContactContactDetails,
        groups = None
      )

      val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)

      val result = Json.toJson[InterimDesRegistration](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }
    "Be able to be parsed into JSON when groups is Some and full" in {
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
                                     |  "companyMemberOfGroup" : true,
                                     |  "groupDetails" : {
                                     |    "parentCompanyName" : "MISTAR FOO",
                                     |    "groupAddress" : {
                                     |                      "line1" : "FOO 1",
                                     |                       "line2" : "FOO 2",
                                     |                       "line3" : "Telford",
                                     |                       "line4" : "Shropshire",
                                     |                       "postcode" : "ZZ1 1ZZ"
                                     |    },
                                     |    "parentUTR" : "1234567890"
                                     |  },
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

      val desBusinessContactContactDetails = BusinessContactDetails(
        Some("0121 000 000"),
        Some("0700 000 000"),
        Some("d@ddd.com")
      )
      val validGroups = Some(Groups(
        groupRelief = true,
        nameOfCompany = Some(GroupCompanyName("MISTAR FOO", GroupCompanyNameEnum.Other)),
        addressAndType = Some(GroupsAddressAndType(GroupAddressTypeEnum.ALF,BusinessAddress(
          "FOO 1",
          "FOO 2",
          Some("Telford"),
          Some("Shropshire"),
          Some("ZZ1 1ZZ"),
          None
        ))),
        Some(GroupUTR(Some("1234567890")))
      ))

      val testInterimCorporationTax = InterimCorporationTax(
        "DG Limited",
        false,
        Some(desBusinessAddress),
        desBusinessContactContactDetails,
        groups = validGroups
      )
      val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)

      val result = Json.toJson[InterimDesRegistration](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }

    "Be able to be parsed into JSON when groups is Some but the group relief has been selected as false by the user" in {
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

      val desBusinessContactContactDetails = BusinessContactDetails(
        Some("0121 000 000"),
        Some("0700 000 000"),
        Some("d@ddd.com")
      )
      val validGroups = Some(Groups(
        groupRelief = false,None,None,None))

      val testInterimCorporationTax = InterimCorporationTax(
        "DG Limited",
        false,
        Some(desBusinessAddress),
        desBusinessContactContactDetails,
        groups = validGroups
      )
      val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)

      val result = Json.toJson[InterimDesRegistration](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }
    "output valid json but group utr was empty" in {
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
                                     |  "companyMemberOfGroup" : true,
                                     |  "groupDetails" : {
                                     |    "parentCompanyName" : "MISTAR FOO",
                                     |    "groupAddress" : {
                                     |                      "line1" : "FOO 1",
                                     |                      "line2" : "FOO 2",
                                     |                      "line3" : "Telford",
                                     |                      "line4" : "Shropshire",
                                     |                      "postcode" : "ZZ1 1ZZ",
                                     |                      "country" : "foo"
                                     |    }
                                     |  },
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

      val desBusinessContactContactDetails = BusinessContactDetails(
        Some("0121 000 000"),
        Some("0700 000 000"),
        Some("d@ddd.com")
      )
      val validGroups = Some(Groups(
        groupRelief = true,
        nameOfCompany = Some(GroupCompanyName("MISTAR FOO", GroupCompanyNameEnum.Other)),
        addressAndType = Some(GroupsAddressAndType(GroupAddressTypeEnum.ALF,BusinessAddress(
          "FOO 1",
          "FOO 2",
          Some("Telford"),
          Some("Shropshire"),
          Some("ZZ1 1ZZ"),
          Some("foo")
        ))),
        Some(GroupUTR(None))
      ))

      val testInterimCorporationTax = InterimCorporationTax(
        "DG Limited",
        false,
        Some(desBusinessAddress),
        desBusinessContactContactDetails,
        groups = validGroups
      )
      val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)

      val result = Json.toJson[InterimDesRegistration](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }


    "Be able to be parsed into JSON when we have a takeover block" in {
      val expectedJson : String = s"""{ "acknowledgementReference" : "ackRef1",
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
                                     |  "hasCompanyTakenOverBusiness" : true,
                                     |  "businessTakeOverDetails" : {
                                     |  "businessNameLine1" : "Takeover name",
                                     |  "businessTakeoverAddress" : {
                                     |                      "line1" : "Takeover 1",
                                     |                      "line2" : "Takeover 2",
                                     |                      "line3" : "TTelford",
                                     |                      "line4" : "TShropshire",
                                     |                      "postcode" : "TO1 1ZZ",
                                     |                      "country" : "UK"
                                     |                      },
                                     |  "prevOwnersName" : "prev name",
                                     |  "prevOwnerAddress" : {
                                     |                      "line1" : "Prev 1",
                                     |                      "line2" : "Prev 2",
                                     |                      "line3" : "PTelford",
                                     |                      "line4" : "PShropshire",
                                     |                      "postcode" : "PR1 1ZZ",
                                     |                      "country" : "UK"
                                     |                      }
                                     |  },
                                     |  "companyMemberOfGroup" : true,
                                     |  "groupDetails" : {
                                     |    "parentCompanyName" : "MISTAR FOO",
                                     |    "groupAddress" : {
                                     |                      "line1" : "FOO 1",
                                     |                       "line2" : "FOO 2",
                                     |                       "line3" : "Telford",
                                     |                       "line4" : "Shropshire",
                                     |                       "postcode" : "ZZ1 1ZZ"
                                     |                       },
                                     |    "parentUTR" : "1234567890"
                                     |  },
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

      val desBusinessContactContactDetails = BusinessContactDetails(
        Some("0121 000 000"),
        Some("0700 000 000"),
        Some("d@ddd.com")
      )
      val validGroups = Some(Groups(
        groupRelief = true,
        nameOfCompany = Some(GroupCompanyName("MISTAR FOO", GroupCompanyNameEnum.Other)),
        addressAndType = Some(GroupsAddressAndType(GroupAddressTypeEnum.ALF,BusinessAddress(
          "FOO 1",
          "FOO 2",
          Some("Telford"),
          Some("Shropshire"),
          Some("ZZ1 1ZZ"),
          None
        ))),
        Some(GroupUTR(Some("1234567890")))
      ))

      val validTakeover = Some(TakeoverDetails(
        replacingAnotherBusiness = true,
        businessName = Some("Takeover name@?><;:+/=(),.!¥#_•€&%£$[]{}~*«»"),
        businessTakeoverAddress = Some(Address(
          "Takeover 1",
          "Takeover 2",
          Some("TTelford"),
          Some("TShropshire"),
          Some("TO1 1ZZ"),
          Some("UK")
        )),
        prevOwnersName = Some("prev name"),
        prevOwnersAddress = Some(Address(
          "Prev 1",
          "Prev 2",
          Some("PTelford"),
          Some("PShropshire"),
          Some("PR1 1ZZ"),
          Some("UK")
        ))
      ))

      val testInterimCorporationTax = InterimCorporationTax(
        "DG Limited",
        false,
        Some(desBusinessAddress),
        desBusinessContactContactDetails,
        groups = validGroups,
        takeOver = validTakeover
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
                                      |  "businessContactDetails" : {
                                      |                             "email" : "d@ddd.com"
                                      |                             }
                                      |                           }
                                      |  }
                                      |}""".stripMargin

      val testMetadata = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), Director )

      val desBusinessContactContactDetails = BusinessContactDetails(
        None,
        None,
        Some("d@ddd.com")
      )

      val testInterimCorporationTax = InterimCorporationTax(
        "DG Limited",
        false,
        None,
        desBusinessContactContactDetails
      )

      val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)

      val result = Json.toJson[InterimDesRegistration](testModel1)
      result.getClass shouldBe classOf[JsObject]
      result shouldBe Json.parse(expectedJson)
    }
    "replace diacritics with equivalent alpha characters for company name" in {
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
                                     |  "companiesHouseCompanyName" : "ss Oscar eg ant",
                                     |  "returnsOnCT61" : false,
                                     |  "companyACharity" : false,
                                     |  "businessContactDetails" : {
                                     |                             "email" : "d@ddd.com"
                                     |                             }
                                     |                           }
                                     |  }
                                     |}""".stripMargin

      val testMetadata = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), Director )
      val desBusinessContactContactDetails = BusinessContactDetails(
                None,
                None,
                Some("d@ddd.com")
                )
      
              val testInterimCorporationTax = InterimCorporationTax(
                "ß Ǭscar ég ànt",
                false,
                None,
                desBusinessContactContactDetails
                )
      
              val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)
      
              val result = Json.toJson[InterimDesRegistration](testModel1)
            result.getClass shouldBe classOf[JsObject]
            result shouldBe Json.parse(expectedJson)
          }
    
          "strip  punctuation characters for company name" in {
            val expectedJson: String =
                s"""{  "acknowledgementReference" : "ackRef1",
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
                                     |  "companiesHouseCompanyName" : "Test Company",
                                     |  "returnsOnCT61" : false,
                                     |  "companyACharity" : false,
                                     |  "businessContactDetails" : {
                                     |                             "email" : "d@ddd.com"
                                     |                             }
                                     |                           }
                                     |  }
                                     |}""".stripMargin
      
            val testMetadata = Metadata( "session-123", "cred-123", "ENG", new DateTime(0).withZone(DateTimeZone.UTC), Director )

            val desBusinessContactContactDetails = BusinessContactDetails(
                None,
                None,
                Some("d@ddd.com")
                )
      
              val testInterimCorporationTax = InterimCorporationTax(
                "[Test Company]»",
                false,
                None,
                desBusinessContactContactDetails
                )
      
              val testModel1 = InterimDesRegistration( "ackRef1", testMetadata, testInterimCorporationTax)
      
              val result = Json.toJson[InterimDesRegistration](testModel1)
            result.getClass shouldBe classOf[JsObject]
            result shouldBe Json.parse(expectedJson)
          }
  }
}
