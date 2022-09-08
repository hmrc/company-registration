/*
 * Copyright 2022 HM Revenue & Customs
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

package audit

import models.des._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, Json}

import java.time.Instant

class DesSubmissionEventSpec extends PlaySpec {

  "DesSubmissionEventDetail" must {

    val regId = "123456789"
    val ackRef = "BRCT1234"
    val timestamp = "2001-12-31T12:00:00.000Z"

    val expected = Json.parse(
      s"""
         |{
         |   "journeyId": "$regId",
         |   "acknowledgementReference": "$ackRef",
         |   "registrationMetadata": {
         |      "businessType": "Limited company",
         |      "formCreationTimestamp": "$timestamp",
         |      "submissionFromAgent": false,
         |      "language": "eng",
         |      "completionCapacity": "Director",
         |      "declareAccurateAndComplete": true
         |   },
         |   "corporationTax": {
         |      "companyOfficeNumber": "623",
         |      "hasCompanyTakenOverBusiness": false,
         |      "companyMemberOfGroup": false,
         |      "companiesHouseCompanyName": "Company Co",
         |      "returnsOnCT61": false,
         |      "companyACharity": false,
         |      "businessAddress": {
         |         "line1": "14 Matheson House",
         |         "line2": "Grange Central",
         |         "line3": "Town Centre",
         |         "line4": "Telford",
         |         "postcode": "TF3 4ER",
         |         "country": "United Kingdom"
         |      },
         |      "businessContactDetails": {
         |         "phoneNumber": "0123456789",
         |         "mobileNumber": "0123456789",
         |         "email": "test@email.co.uk"
         |      }
         |   }
         |}
        """.stripMargin)

    "construct full json as per definition" in {
      val testModel = DesSubmissionAuditEventDetail(
        regId,
        Json.toJson(InterimDesRegistration(
          ackRef,
          Metadata(
            "sessionId", "credId", "eng", Instant.parse(timestamp), CompletionCapacity("Director")
          ),
          InterimCorporationTax(
            "Company Co",
            returnsOnCT61 = false,
            Some(BusinessAddress("14 Matheson House", "Grange Central", Some("Town Centre"), Some("Telford"), Some("TF3 4ER"), Some("United Kingdom"))),
            BusinessContactDetails(
              Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
            )
          )
        )).as[JsObject]
      )
      Json.toJson(testModel)(DesSubmissionAuditEventDetail.writes) mustBe expected
    }

    "construct full json as per definition when company name has characters allowed by companies house but not DES" in {
      val testModel = DesSubmissionAuditEventDetail(
        regId,
        Json.toJson(InterimDesRegistration(
          ackRef,
          Metadata(
            "sessionId", "credId", "eng", Instant.parse(timestamp), CompletionCapacity("Director")
          ),
          InterimCorporationTax(
            "Company Co[]{}#$\\«»",
            returnsOnCT61 = false,
            Some(BusinessAddress("14 Matheson House", "Grange Central", Some("Town Centre"), Some("Telford"), Some("TF3 4ER"), Some("United Kingdom"))),
            BusinessContactDetails(
              Some("0123456789"), Some("0123456789"), Some("test@email.co.uk")
            )
          )
        )).as[JsObject]
      )
      Json.toJson(testModel)(DesSubmissionAuditEventDetail.writes) mustBe expected
    }
  }
}
