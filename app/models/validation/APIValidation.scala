/*
 * Copyright 2024 HM Revenue & Customs
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

import auth.CryptoSCRS
import models.AccountPrepDetails.{COMPANY_DEFINED, HMRC_DEFINED}
import models.AccountingDetails.{FUTURE_DATE => FD, NOT_PLANNING_TO_YET => NP2Y, WHEN_REGISTERED => WR}
import models._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{maxLength, pattern}
import play.api.libs.json._
import utils.{Logging, StringNormaliser}

import java.time.LocalDate
import scala.util.Try
import scala.util.matching.Regex

object APIValidation extends APIValidation

trait APIValidation extends BaseJsonFormatting
  with ContactDetailsValidator
  with AddressValidator
  with DateValidator with GroupsValidator {

  val ackRefValidator: Format[String] = readToFmt(maxLength[String](31))

  val acctStatusValidator: Format[String] = readToFmt(pattern(s"^$WR|$FD|$NP2Y$$".r))
  val tradingDetailsValidator: Reads[String] = readToFmt(pattern("""^(true|false)$""".r, "expected either 'true' or 'false' but neither was found"))
  val acctPrepStatusValidator: Format[String] = readToFmt(pattern(s"^$COMPANY_DEFINED|$HMRC_DEFINED$$".r))

  def accountPrepDetailsFormatWithFilter(formatDef: OFormat[AccountPrepDetails]): Format[AccountPrepDetails] = {
    withFilter[AccountPrepDetails](formatDef, JsonValidationError("If a date is specified, the status must be COMPANY_DEFINED")) {
      aPD => if (aPD.endDate.isDefined) aPD.status == COMPANY_DEFINED else aPD.status != COMPANY_DEFINED
    }
  }

}

trait GroupsValidator extends Logging {
  self: BaseJsonFormatting =>

  override def formatsForGroupCompanyNameEnum(name: String): Format[GroupCompanyNameEnum.Value] = new Format[GroupCompanyNameEnum.Value] {
    override def reads(json: JsValue): JsResult[GroupCompanyNameEnum.Value] = {
      json.validate[String].flatMap(str =>
        Try(GroupCompanyNameEnum.withName(str))
          .toOption
          .fold[JsResult[GroupCompanyNameEnum.Value]](JsError(s"String value is not an enum: $str")) { success =>
            if (name.length > 20 && success == GroupCompanyNameEnum.Other) {
              logger.warn("[Groups API Reads] nameOfCompany.nameType = Other but name.size > 20, could indicate frontend validation issue")
            }
            JsSuccess(success)
          })
    }

    override def writes(o: GroupCompanyNameEnum.Value): JsValue = JsString(o.toString)
  }

  override def formatsForGroupAddressType: Format[GroupAddressTypeEnum.Value] = new Format[GroupAddressTypeEnum.Value] {
    override def reads(json: JsValue): JsResult[GroupAddressTypeEnum.Value] = {
      json.validate[String].flatMap(str =>
        Try(GroupAddressTypeEnum.withName(str))
          .toOption
          .fold[JsResult[GroupAddressTypeEnum.Value]](JsError(s"String value is not an enum: $str")) { success =>
            JsSuccess(success)
          })
    }

    override def writes(o: GroupAddressTypeEnum.Value): JsValue = JsString(o.toString)
  }

  override def utrFormats(cryptoSCRS: CryptoSCRS): Format[String] = new Format[String] {
    override def writes(o: String): JsValue = JsString(o)

    override def reads(json: JsValue): JsResult[String] = json.validate[String].flatMap {
      str => {
        if (str.replaceAll(" ", "").matches("""[0-9]{1,10}""")) {
          JsSuccess(str)
        } else {
          JsError("UTR does not match regex")
        }
      }
    }
  }
}

trait ContactDetailsValidator {
  self: BaseJsonFormatting =>

  def contactDetailsFormatWithFilter(formatDef: OFormat[ContactDetails]): Format[ContactDetails] = {
    withFilter(formatDef, JsonValidationError("Must have at least one email, phone or mobile specified")) {
      cD => cD.mobile.isDefined || cD.phone.isDefined || cD.email.isDefined
    }
  }

  val phoneValidator: Format[String] = readToFmt(length(20) keepAnd pattern("^[0-9 ]{1,20}$".r) keepAnd digitLength(10, 20))
  val emailValidator: Format[String] = readToFmt(length(70) keepAnd pattern("^[A-Za-z0-9\\-_.@]{1,70}$".r))
}


trait DateValidator {
  self: BaseJsonFormatting =>

  def yyyymmddValidator: Reads[String] = pattern("^[0-9]{4}-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])$".r)

  val startDateValidator: Format[String] = readToFmt(yyyymmddValidator)

  def accountingDetailsFormatWithFilter(formatDef: OFormat[AccountingDetails]): Format[AccountingDetails] = {
    import AccountingDetails.FUTURE_DATE

    withFilter[AccountingDetails](formatDef, JsonValidationError("If a date is specified, the status must be FUTURE_DATE")) {
      aD => if (aD.activeDate.isDefined) aD.status == FUTURE_DATE else aD.status != FUTURE_DATE
    }
  }

  val dateFormat: Format[LocalDate] = Format[LocalDate](
    Reads[LocalDate](_.validate[String](yyyymmddValidator).map(LocalDate.parse)),
    Writes[LocalDate](d => JsString(d.toString))
  )
}

trait AddressValidator extends Logging {
  self: BaseJsonFormatting =>

  val chPremisesValidator: Format[String] = lengthFmt(120)
  val chLineValidator: Format[String] = lengthFmt(50)
  val chPostcodeValidator: Format[String] = lengthFmt(20)
  val chRegionValidator: Format[String] = chLineValidator

  def ppobAddressFormatWithFilter(formatDef: OFormat[PPOBAddress]): Format[PPOBAddress] = {
    withFilter(formatDef, JsonValidationError("Must have at least one of postcode and country")) {
      ppob => ppob.postcode.isDefined || ppob.country.isDefined
    }
  }

  private def regexWrap(regex: String): Regex = {
    regex.r
  }

  val linePattern = regexWrap("""[a-zA-Z0-9\/\\("),.'&:;-]{1}[a-zA-Z0-9\/\\("), .'&:;-]{0,26}""")
  val line4Pattern = regexWrap("""[a-zA-Z0-9\/\\("),.'&:;-]{1}[a-zA-Z0-9\/\\("), .'&:;-]{0,17}""")
  val postCodePattern = regexWrap("[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}")
  val countryPattern = regexWrap("[A-Za-z0-9]{1}[A-Za-z 0-9]{0,19}")
  val parentGroupNamePattern = regexWrap("""[A-Z a-z 0-9\\'-]{1,20}$""")

  val lineInvert = regexWrap("""[a-zA-Z0-9\/\\("), .'&;:-]""")
  val postCodeInvert = regexWrap("[A-Z0-9 ]")
  val countryInvert = regexWrap("[A-Za-z0-9 ]")
  //Groups
  val parentGroupNameInvert = regexWrap("""[A-Z a-z 0-9\\'-]""")
  val takeoverNameInvert = regexWrap("""[A-Za-z 0-9\\'-]""")

  def normaliseStringReads(regex: Regex, amountToTake: Int)(implicit implReads: Reads[String]): Reads[String] = new Reads[String] {
    override def reads(json: JsValue): JsResult[String] = {
      implReads.reads(json).flatMap { theString =>
        val string = StringNormaliser.normaliseString(theString, regex)
        if (theString.nonEmpty && string.isEmpty) {
          JsError("error.not.normalisable")
        } else {
          JsSuccess(string.take(amountToTake))
        }
      }
    }
  }

  def chainedNormaliseReads(regex: Regex, maxLength: Int) = {
    length(maxLength)(normaliseStringReads(regex, maxLength))
  }

  val parentGroupNameValidator = readToFmt(pattern(parentGroupNamePattern)(chainedNormaliseReads(parentGroupNameInvert, 20)))

  val lineValidator = readToFmt(pattern(linePattern)(chainedNormaliseReads(lineInvert, 27)))
  val line4Validator = readToFmt(pattern(line4Pattern)(chainedNormaliseReads(lineInvert, 18)))
  val postcodeValidator = readToFmt(pattern(postCodePattern)(chainedNormaliseReads(postCodeInvert, 20)))
  val countryValidator = readToFmt(pattern(countryPattern)(chainedNormaliseReads(countryInvert, 20)))

  override def groupNameValidation: Format[String] = new Format[String] {
    override def reads(json: JsValue): JsResult[String] = {
      json.validate[String](parentGroupNameValidator).fold(err => {
        logger.warn("[Groups API Reads] name of company invalid when normalised and trimmed this doesn't pass Regex validation")
        JsError(err)
      }, success => JsSuccess(json.as[String]))
    }

    override def writes(o: String): JsValue = JsString(o)
  }
}