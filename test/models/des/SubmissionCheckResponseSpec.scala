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

package models.des

import models.{IncorpUpdate, SubmissionCheckResponse}
import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class SubmissionCheckResponseSpec extends UnitSpec {

  "SubmissionCheckResponse" should {

    "Be able to read from JSON where no updates were returned" in {

      val json1: String =
        s"""
           |{
           |  "items": [],
           |  "links": {
           |    "next": "https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
           |  }
           |}
       """.stripMargin

      val testModel1 = SubmissionCheckResponse(Seq(), "https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789")

      Json.fromJson[SubmissionCheckResponse](Json.parse(json1)).get shouldBe testModel1
    }



    "Be able to read from JSON where one update is returned" in {

      val json1: String =
        s"""
           |{
           |  "items": [
           |    {"company_number" : "99999999",        "transaction_status" : "accepted",
           |     "transaction_type" : "incorporation", "company_profile_link" : "http://api.companieshouse.gov.uk/company/99999999",
           |     "transaction_id" : "0987654322",      "incorporated_on" : "2016-08-10",
           |     "timepoint": "123456787"
           |    }
           |  ],
           |  "links": {
           |    "next": "https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
           |  }
           |}
       """.stripMargin

      val testIncorpUpdate = IncorpUpdate("0987654322", "accepted", Some("99999999"), Some(new DateTime(2016, 8, 10, 0, 0)), "123456787")
      val testModel1 = SubmissionCheckResponse(Seq(testIncorpUpdate), "https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789")

      Json.fromJson[SubmissionCheckResponse](Json.parse(json1)).get shouldBe testModel1
    }



    "Be able to read from JSON where many updates were returned" in {

      val json1: String =
        s"""
           |{
           |  "items": [
           |    {"company_number" : "99999999",        "transaction_status" : "accepted",
           |     "transaction_type" : "incorporation", "company_profile_link" : "http://api.companieshouse.gov.uk/company/99999999",
           |     "transaction_id" : "0987654322",      "incorporated_on" : "2016-08-10",
           |     "timepoint": "123456787"
           |    },
           |    {"company_number" : "99999998",        "transaction_status" : "accepted",
           |     "transaction_type" : "incorporation", "company_profile_link" : "http://api.companieshouse.gov.uk/company/99999998",
           |     "transaction_id" : "0987654321",      "incorporated_on" : "2016-08-10",
           |     "timepoint": "123456789"
           |    }
           |  ],
           |  "links": {
           |    "next": "https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
           |  }
           |}
       """.stripMargin

      val testIncorpUpdate = IncorpUpdate("0987654322", "accepted", Some("99999999"), Some(new DateTime(2016, 8, 10, 0, 0)), "123456787")
      val testIncorpUpdate2 = IncorpUpdate("0987654321","accepted", Some("99999998"), Some(new DateTime(2016, 8, 10, 0, 0)), "123456789")

      val testModel1 = SubmissionCheckResponse(
        Seq(testIncorpUpdate,testIncorpUpdate2),
        "https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
      )

      Json.fromJson[SubmissionCheckResponse](Json.parse(json1)).get shouldBe testModel1
    }

    "Be able to read from JSON when an incorporation was rejected" in {

      val json1: String =
        s"""
           |{
           |  "items": [
           |    {
           |     "transaction_status" : "rejected",
           |     "transaction_type" : "incorporation",
           |     "transaction_id" : "0987654322",
           |     "timepoint": "123456787",
           |     "transaction_status_description": "reason"
           |    }
           |  ],
           |  "links": {
           |    "next": "https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
           |  }
           |}
       """.stripMargin

      val testIncorpUpdate = IncorpUpdate("0987654322", "rejected", None, None, "123456787", Some("reason"))

      val testModel1 = SubmissionCheckResponse(
        Seq(testIncorpUpdate),
        "https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
      )

      Json.fromJson[SubmissionCheckResponse](Json.parse(json1)).get shouldBe testModel1
    }
  }

}
