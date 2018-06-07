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

package models.validation
import models.AccountPrepDetails.{COMPANY_DEFINED, HMRC_DEFINED}
import models.AccountingDetails.{FUTURE_DATE => FD, NOT_PLANNING_TO_YET => NP2Y, WHEN_REGISTERED => WR}
import models.{AccountPrepDetails, AccountingDetails, ContactDetails, PPOBAddress}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import play.api.data.validation.ValidationError
import play.api.libs.json.Reads.{maxLength, pattern}
import play.api.libs.json.{Format, JsString, OFormat, Reads, Writes}
import play.api.libs.functional.syntax._

import scala.util.matching.Regex

object APIValidation extends APIValidation

trait APIValidation extends BaseJsonFormatting
    with ContactDetailsValidator
    with AddressValidator
    with DateValidator {

  val ackRefValidator: Format[String] = readToFmt(maxLength[String](31))

  val acctStatusValidator: Format[String] = readToFmt(pattern(s"^$WR|$FD|$NP2Y$$".r))
  val tradingDetailsValidator: Reads[String] = readToFmt(pattern("""^(true|false)$""".r, "expected either 'true' or 'false' but neither was found"))
  val acctPrepStatusValidator: Format[String] = readToFmt(pattern(s"^$COMPANY_DEFINED|$HMRC_DEFINED$$".r))

  def accountPrepDetailsFormatWithFilter(formatDef: OFormat[AccountPrepDetails]): Format[AccountPrepDetails] = {
    withFilter[AccountPrepDetails](formatDef, ValidationError("If a date is specified, the status must be COMPANY_DEFINED")) {
      aPD => if (aPD.endDate.isDefined) aPD.status == COMPANY_DEFINED else aPD.status != COMPANY_DEFINED
    }
  }
}

trait ContactDetailsValidator {
  self: BaseJsonFormatting =>

  def contactDetailsFormatWithFilter(formatDef: OFormat[ContactDetails]): Format[ContactDetails] = {
    withFilter(formatDef, ValidationError("Must have at least one email, phone or mobile specified")) {
      cD => cD.mobile.isDefined || cD.phone.isDefined || cD.email.isDefined
    }
  }

  val nameValidator: Format[String] = readToFmt(length(100) keepAnd pattern("^[A-Za-z 0-9'\\\\-]{1,100}$".r))
  val phoneValidator: Format[String] = readToFmt(length(20) keepAnd pattern("^[0-9 ]{1,20}$".r) keepAnd digitLength(10, 20))
  val emailValidator: Format[String] = readToFmt(length(70) keepAnd pattern("^[A-Za-z0-9\\-_.@]{1,70}$".r))
}


trait DateValidator {
  self: BaseJsonFormatting =>

  def now: DateTime = DateTime.now(DateTimeZone.UTC)
  def yyyymmddValidator: Reads[String] = pattern("^[0-9]{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$".r)

  val startDateValidator: Format[String] = readToFmt(yyyymmddValidator)

  def accountingDetailsFormatWithFilter(formatDef: OFormat[AccountingDetails]): Format[AccountingDetails] = {
    import AccountingDetails.FUTURE_DATE

    withFilter[AccountingDetails](formatDef, ValidationError("If a date is specified, the status must be FUTURE_DATE")) {
      aD => if (aD.activeDate.isDefined) aD.status == FUTURE_DATE else aD.status != FUTURE_DATE
    }
  }

  val dateFormat: Format[DateTime] = Format[DateTime](
    Reads[DateTime](js =>
      js.validate[String](yyyymmddValidator).map(
        DateTime.parse(_, DateTimeFormat.forPattern(dateTimePattern))
      )
    ),
    new Writes[DateTime] {
      def writes(d: DateTime) = JsString(d.toString(dateTimePattern))
    }
  )
}


trait AddressValidator {
  self: BaseJsonFormatting =>

  val chPremisesValidator: Format[String] = lengthFmt(120)
  val chLineValidator: Format[String] = lengthFmt(50)
  val chPostcodeValidator: Format[String] = lengthFmt(20)
  val chRegionValidator: Format[String] = chLineValidator

  def ppobAddressFormatWithFilter(formatDef: OFormat[PPOBAddress]): Format[PPOBAddress] = {
    withFilter(formatDef, ValidationError("Must have at least one of postcode and country")) {
      ppob => ppob.postcode.isDefined || ppob.country.isDefined
    }
  }

  private def regexWrap(regex : String): Regex = {
    ("^" + regex + "$").r
  }

  val linePattern = regexWrap("[a-zA-Z0-9,.\\(\\)/&amp;'&quot;\\-]{1}[a-zA-Z0-9, .\\(\\)/&amp;'&quot;\\-]{0,26}")
  val line4Pattern = regexWrap("[a-zA-Z0-9,.\\(\\)/&amp;'&quot;\\-]{1}[a-zA-Z0-9, .\\(\\)/&amp;'&quot;\\-]{0,17}")
  val postCodePattern = regexWrap("[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}")
  val countryPattern = regexWrap("[A-Za-z0-9]{1}[A-Za-z 0-9]{0,19}")

  val lineInvert = regexWrap("[a-zA-Z0-9,.\\(\\)/&amp;'&quot;\\- ]")
  val postCodeInvert = regexWrap("[A-Z0-9 ]")
  val countryInvert = regexWrap("[A-Za-z0-9 ]")

  val lineValidator = readToFmt(length(27) keepAnd pattern(linePattern))
  val line4Validator = readToFmt(length(18) keepAnd pattern(line4Pattern))
  val postcodeValidator = readToFmt(length(20) keepAnd pattern(postCodePattern))
  val countryValidator = readToFmt(length(20) keepAnd pattern(countryPattern))
}