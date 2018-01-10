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

import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec

class AccountingDetailsSpec extends UnitSpec with JsonFormatValidation {

  import AccountingDetails.{WHEN_REGISTERED,FUTURE_DATE,NOT_PLANNING_TO_YET}

  "AccountingDetails Model" should {
    "Be able to be parsed from JSON" in {
      val json = """{"accountingDateStatus":"FUTURE_DATE","startDateOfBusiness":"2017-02-01"}"""
      val expected = AccountingDetails(FUTURE_DATE, Some("2017-02-01"))
      val result = Json.parse(json).validate[AccountingDetails]
      shouldBeSuccess(expected, result)
    }

    "fail if status is not valid" in {
      val json = """{"accountingDateStatus":"XXX"}"""
      val result = Json.parse(json).validate[AccountingDetails]
      shouldHaveErrors(result, JsPath() \ "accountingDateStatus", Seq(ValidationError("error.pattern")))
    }

    "fail if the date format isn't valid" in {
      val json = """{"accountingDateStatus":"FUTURE_DATE","startDateOfBusiness":"2017/02/01"}"""
      val result = Json.parse(json).validate[AccountingDetails]
      shouldHaveErrors(result, JsPath() \ "startDateOfBusiness", Seq(ValidationError("error.pattern")))
    }
  }

  "reading from json into a AccountingDetails case class" should {

    "return a AccountingDetails case class" in {
      val accountingDetails = AccountingDetails(FUTURE_DATE, Some("2017-02-01"))
      val json = Json.parse("""{"accountingDateStatus":"FUTURE_DATE","startDateOfBusiness":"2017-02-01"}""")

      val result = Json.fromJson[AccountingDetails](json)
      result shouldBe JsSuccess(accountingDetails)
    }

    "throw a json validation error when the status is future date but a date is not provided" in {
      val json = Json.parse("""{"accountingDateStatus":"FUTURE_DATE"}""")

      val result = Json.fromJson[AccountingDetails](json)
      shouldHaveErrors(result, JsPath(), Seq(ValidationError("If a date is specified, the status must be FUTURE_DATE")))
    }
  }

}
