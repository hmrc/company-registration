/*
 * Copyright 2019 HM Revenue & Customs
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

import java.text.Normalizer
import java.text.Normalizer.Form

import auth.CryptoSCRS
import models.{AccountPrepDetails, AccountingDetails, ContactDetails, PPOBAddress}
import org.joda.time.DateTime
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{maxLength, minLength}
import play.api.libs.json.{Format, JsError, JsSuccess, JsValue, OFormat, Reads, Writes}

trait BaseJsonFormatting {
  private val companyNameRegex = """^[A-Za-z 0-9\-,.()/'&\"!%*_+:@<>?=;]{1,160}$"""
  private val forbiddenPunctuation = Set('[', ']', '{', '}', '#', '«', '»')
  private val illegalCharacters = Map('æ' -> "ae", 'Æ' -> "AE", 'œ' -> "oe", 'Œ' -> "OE", 'ß' -> "ss", 'ø' -> "o", 'Ø' -> "O")

  val dateTimePattern = "yyyy-MM-dd"

  def length(maxLen: Int, minLen: Int = 1)(implicit reads: Reads[String]): Reads[String] = maxLength[String](maxLen) keepAnd minLength[String](minLen)

  def readToFmt(rds: Reads[String])(implicit wts: Writes[String]): Format[String] = Format(rds, wts)

  def digitLength(minLength: Int, maxLength: Int)(implicit wts: Writes[String]):Format[String] = {
    val reads: Reads[String] = new Reads[String] {
      override def reads(json: JsValue) = {
        val str = json.as[String]
        if(str.replaceAll(" ", "").matches(s"[0-9]{$minLength,$maxLength}")) {
          JsSuccess(str)
        } else {
          JsError(s"field must contain between $minLength and $maxLength numbers")
        }
      }
    }

    Format(reads, wts)
  }

  def lengthFmt(maxLen: Int, minLen: Int = 1): Format[String] = readToFmt(length(maxLen, minLen))

  def withFilter[A](fmt: Format[A], error: ValidationError)(f: (A) => Boolean): Format[A] = {
    Format(fmt.filter(error)(f), fmt)
  }

  def standardRead: Reads[String] = Reads.StringReads

  def cleanseCompanyName(companyName: String): String = Normalizer.normalize(
    companyName.map(c => if(illegalCharacters.contains(c)) illegalCharacters(c) else c).mkString,
    Form.NFD
  ).replaceAll("[^\\p{ASCII}]", "").filterNot(forbiddenPunctuation)

  //Acknowledgement Reference
  def cryptoFormat(crypto: CryptoSCRS): Format[String] = Format(Reads.StringReads, Writes.StringWrites)

  //Contact Details
  def contactDetailsFormatWithFilter(formatDef: OFormat[ContactDetails]): Format[ContactDetails]
  val phoneValidator: Format[String]
  val emailValidator: Format[String]

  val companyNameValidator: Format[String] = readToFmt(Reads.StringReads.filter(ValidationError("Invalid company name"))(companyName => cleanseCompanyName(companyName).matches(companyNameRegex)))

  //CHROAddress
  val chPremisesValidator: Format[String]
  val chLineValidator: Format[String]
  val chPostcodeValidator: Format[String]
  val chRegionValidator: Format[String]

  //PPOBAddress
  def ppobAddressFormatWithFilter(formatDef: OFormat[PPOBAddress]): Format[PPOBAddress]
  val lineValidator: Format[String]
  val line4Validator: Format[String]
  val postcodeValidator: Format[String]
  val countryValidator: Format[String]

  //ConfirmationReferences
  val ackRefValidator: Format[String]

  //AccountingDetails
  def accountingDetailsFormatWithFilter(formatDef: OFormat[AccountingDetails]): Format[AccountingDetails]
  val acctStatusValidator: Format[String]
  val startDateValidator: Format[String]

  //TradingDetails
  val tradingDetailsValidator: Reads[String]

  //AccountPrepDetails
  def accountPrepDetailsFormatWithFilter(formatDef: OFormat[AccountPrepDetails]): Format[AccountPrepDetails]
  val acctPrepStatusValidator: Format[String]
  val dateFormat: Format[DateTime]
}