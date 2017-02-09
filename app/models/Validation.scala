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

package models

import play.api.data.validation.ValidationError
import play.api.libs.json._
import Reads.{maxLength, minLength, pattern}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.functional.syntax._


object Validation {

  def length(maxLen: Int, minLen: Int = 1): Reads[String] = maxLength[String](maxLen) keepAnd minLength[String](minLen)

  def readToFmt(rds: Reads[String])(implicit wts: Writes[String]): Format[String] = Format(rds, wts)

  def lengthFmt(maxLen: Int, minLen: Int = 1): Format[String] = readToFmt(length(maxLen, minLen))

  def yyyymmddValidator = pattern("^[0-9]{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$".r)

  def yyyymmddValidatorFmt = readToFmt(yyyymmddValidator)

  def withFilter[A](fmt: Format[A], error: ValidationError)(f: (A) => Boolean): Format[A] = {
    Format(fmt.filter(error)(f), fmt)
  }
}

trait CHAddressValidator {

  import Validation.lengthFmt

  val premisesValidator = lengthFmt(120)
  val lineValidator = lengthFmt(50)
  val postcodeValidator = lengthFmt(20)
  val regionValidator = lineValidator
}

trait HMRCAddressValidator {

  import Validation._

  val lineValidator = readToFmt(length(27) keepAnd pattern("^[a-zA-Z0-9,.\\(\\)/&amp;'&quot;\\-]{1}[a-zA-Z0-9, .\\(\\)/&amp;'&quot;\\-]{0,26}$".r))
  val postcodeValidator = readToFmt(length(20) keepAnd pattern("^[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}$".r))
  val countryValidator = readToFmt(length(20) keepAnd pattern("^[A-Za-z0-9]{1}[A-Za-z 0-9]{0,19}$".r))
}

trait CompanyDetailsValidator {

  import Validation._

  val companyNameValidator = readToFmt(pattern("^[A-Za-z 0-9\\-,.()/'&amp;&quot;!%*_+:@&lt;&gt;?=;]{1,160}$".r))
}

trait ContactDetailsValidator {

  import Validation._

  val nameValidator = readToFmt(length(100) keepAnd pattern("^[A-Za-z 0-9'\\\\-]{1,100}$".r))
  val phoneValidator = readToFmt(length(20) keepAnd pattern("^[0-9 ]{1,20}$".r))
  val emailValidator = readToFmt(length(70) keepAnd pattern("^[A-Za-z0-9\\-_.@]{1,70}$".r))
}

trait AccountingDetailsValidator {

  import Validation._
  import AccountingDetails.{WHEN_REGISTERED => WR, FUTURE_DATE => FD, NOT_PLANNING_TO_YET => NP2Y}

  val statusValidator = readToFmt(pattern(s"^${WR}|${FD}|${NP2Y}$$".r))
  val startDateValidator = yyyymmddValidatorFmt
}

trait AccountPrepDetailsValidator {

  import Validation._
  import AccountPrepDetails.{COMPANY_DEFINED, HMRC_DEFINED}

  val statusValidator = readToFmt(pattern(s"^${COMPANY_DEFINED}|${HMRC_DEFINED}$$".r))
  val dateFormat = Format[DateTime](
    Reads[DateTime](js =>
      js.validate[String](yyyymmddValidator).map(
        DateTime.parse(_, DateTimeFormat.forPattern("yyyy-MM-dd"))
      )
    ),
    new Writes[DateTime] {
      def writes(d: DateTime) = JsString(d.toString("yyyy-MM-dd"))
    }
  )
}
