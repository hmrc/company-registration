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

package audit


import org.joda.time.DateTime
import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.play.test.UnitSpec

class DesTopUpEventSpec extends UnitSpec {

  "DesTopUpEventDetail" should {

    val regId = "123456789"
    val crn = "0123456789"
    val accepted = "accepted"
    val rejected = "rejected"
    val ackRef = "BRCT1234"


    "construct full accepted json as per definition" in {
      val expected = Json.parse(
        s"""
           |{
           |   "journeyId": "$regId",
           |   "acknowledgementReference": "$ackRef",
           |   "incorporationStatus" : "$accepted",
           |   "intendedAccountsPreparationDate" : "2017-01-02",
           |   "startDateOfFirstAccountingPeriod" : "2017-01-04",
           |   "companyActiveDate" : "2017-01-01",
           |   "crn" : "$crn"
           |   }
        """.stripMargin)

      val testModel = DesTopUpSubmissionEventDetail(
        regId,
        ackRef,
        accepted,
        Some(DateTime.parse("2017-01-02")),
        Some(DateTime.parse("2017-01-04")),
        Some(DateTime.parse("2017-01-01")),
        Some(crn)
      )

      Json.toJson(testModel)(DesTopUpSubmissionEventDetail.writes) shouldBe expected
    }


  "construct full rejected json as per definition" in {
    val expected = Json.parse(
      s"""
         |{
         |   "journeyId": "$regId",
         |   "acknowledgementReference": "$ackRef",
         |   "incorporationStatus" : "$rejected"
         |   }
        """.stripMargin)

    val testModel = DesTopUpSubmissionEventDetail(
      regId,
      ackRef,
      rejected,
      None,
      None,
      None,
      None
    )

    Json.toJson(testModel)(DesTopUpSubmissionEventDetail.writes) shouldBe expected
  }}


 }
