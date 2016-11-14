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

import helpers.DateHelper
import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec

class AccountPrepDetailsSpec extends UnitSpec with JsonFormatValidation with DateHelper {

  import AccountPrepDetails.COMPANY_DEFINED

  "AccountPrepDetails Model" should {
    "Be able to be parsed from JSON" in {
      val json = """{"businessEndDateChoice":"COMPANY_DEFINED","businessEndDate":"2017-02-01"}"""
      val expected = AccountPrepDetails(COMPANY_DEFINED, Some(yyyymmdd("2017-02-01")))
      val result = Json.parse(json).validate[AccountPrepDetails]
      shouldBeSuccess(expected, result)
    }

    "fail if choice is not valid" in {
      val json = """{"businessEndDateChoice":"XXX"}"""
      val result = Json.parse(json).validate[AccountPrepDetails]
      shouldHaveErrors(result, JsPath() \ "businessEndDateChoice", Seq(ValidationError("error.pattern")))
    }

    "fail if the date format isn't valid" in {
      val json = """{"businessEndDateChoice":"COMPANY_DEFINED","businessEndDate":"2017/02/01"}"""
      val result = Json.parse(json).validate[AccountPrepDetails]
      shouldHaveErrors(result, JsPath() \ "businessEndDate", Seq(ValidationError("error.pattern")))
    }

    "(must) have a date if company defined is specified" in {
      val json = """{"businessEndDateChoice":"COMPANY_DEFINED"}"""

      intercept[IllegalArgumentException] {
        Json.parse(json).validate[AccountPrepDetails]
      }
    }

    "not have a date if HMRC defined is specified" in {
      val json = """{"businessEndDateChoice":"HMRC_DEFINED","businessEndDate":"2017-02-01"}"""

      intercept[IllegalArgumentException] {
        Json.parse(json).validate[AccountPrepDetails]
      }
    }
  }

}
