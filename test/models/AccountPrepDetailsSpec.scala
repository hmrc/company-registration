/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsonValidationError, _}

class AccountPrepDetailsSpec extends PlaySpec with JsonFormatValidation with DateHelper {

  import AccountPrepDetails.COMPANY_DEFINED

  "AccountPrepDetails Model" must {
    "Be able to be parsed from JSON" in {
      val json = """{"businessEndDateChoice":"COMPANY_DEFINED","businessEndDate":"2017-02-01"}"""
      val expected = AccountPrepDetails(COMPANY_DEFINED, Some(asDate("2017-02-01")))
      val result = Json.parse(json).validate[AccountPrepDetails]
      mustBeSuccess(expected, result)
    }

    "fail if choice is not valid" in {
      val json = """{"businessEndDateChoice":"XXX"}"""
      val result = Json.parse(json).validate[AccountPrepDetails]
      shouldHaveErrors(result, JsPath() \ "businessEndDateChoice", Seq(JsonValidationError("error.pattern")))
    }

    "fail if the date format isn't valid" in {
      val json = """{"businessEndDateChoice":"COMPANY_DEFINED","businessEndDate":"2017/02/01"}"""
      val result = Json.parse(json).validate[AccountPrepDetails]
      shouldHaveErrors(result, JsPath() \ "businessEndDate", Seq(JsonValidationError("error.pattern")))
    }
  }

  "reading from json into a AccountPrepDetails case class" must {

    "return a AccountPrepDetails case class" in {
      val accountPrepDetails = AccountPrepDetails(COMPANY_DEFINED, Some(asDate("2017-02-01")))
      val json = Json.parse("""{"businessEndDateChoice":"COMPANY_DEFINED","businessEndDate":"2017-02-01"}""")

      val result = Json.fromJson[AccountPrepDetails](json)
      result mustBe JsSuccess(accountPrepDetails)
    }

    "throw a json validation error when the status is company defined but a date is not provided" in {
      val json = Json.parse("""{"businessEndDateChoice":"COMPANY_DEFINED"}""")

      val result = Json.fromJson[AccountPrepDetails](json)
      shouldHaveErrors(result, JsPath(), Seq(JsonValidationError("If a date is specified, the status must be COMPANY_DEFINED")))
    }
  }

}
