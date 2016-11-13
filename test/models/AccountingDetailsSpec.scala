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

    "(must) have a date if future date is specified" in {
      val json = """{"accountingDateStatus":"FUTURE_DATE"}"""

      intercept[IllegalArgumentException] {
        Json.parse(json).validate[AccountingDetails]
      }
    }

    "only have a date if future date is specified" in {
      val json = """{"accountingDateStatus":"WHEN_REGISTERED","startDateOfBusiness":"2017-02-01"}"""

      intercept[IllegalArgumentException] {
        Json.parse(json).validate[AccountingDetails]
      }
    }
  }

}