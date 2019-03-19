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
import auth.CryptoSCRS
import models.{AccountPrepDetails, AccountingDetails, ContactDetails, PPOBAddress}
import org.joda.time.DateTime
import play.api.libs.json.{Format, OFormat, Reads, Writes}

object MongoValidation extends BaseJsonFormatting {
  val defaultStringFormat = Format(Reads.StringReads, Writes.StringWrites)

  override def cryptoFormat(crypto: CryptoSCRS): Format[String] = Format(crypto.rds, crypto.wts)

  //ContactDetails
  def contactDetailsFormatWithFilter(formatDef: OFormat[ContactDetails]): Format[ContactDetails] = formatDef
  val phoneValidator = defaultStringFormat
  val emailValidator = defaultStringFormat

  override val companyNameValidator = defaultStringFormat

  //CHROAddress
  val chPremisesValidator: Format[String] = defaultStringFormat
  val chLineValidator: Format[String] = defaultStringFormat
  val chPostcodeValidator: Format[String] = defaultStringFormat
  val chRegionValidator: Format[String] = defaultStringFormat

  //PPOBAddress
  def ppobAddressFormatWithFilter(formatDef: OFormat[PPOBAddress]): Format[PPOBAddress] = formatDef
  val lineValidator: Format[String] = defaultStringFormat
  val line4Validator: Format[String] = defaultStringFormat
  val postcodeValidator: Format[String] = defaultStringFormat
  val countryValidator: Format[String] = defaultStringFormat

  val ackRefValidator: Format[String] = defaultStringFormat

  def accountingDetailsFormatWithFilter(formatDef: OFormat[AccountingDetails]): Format[AccountingDetails] = formatDef
  val acctStatusValidator: Format[String] = defaultStringFormat
  val startDateValidator: Format[String] = defaultStringFormat

  val tradingDetailsValidator: Reads[String] = defaultStringFormat

  def accountPrepDetailsFormatWithFilter(formatDef: OFormat[AccountPrepDetails]): Format[AccountPrepDetails] = formatDef
  val acctPrepStatusValidator: Format[String] = defaultStringFormat
  val dateFormat: Format[DateTime] = Format(Reads.DefaultJodaDateReads, Writes.jodaDateWrites(dateTimePattern))
}
