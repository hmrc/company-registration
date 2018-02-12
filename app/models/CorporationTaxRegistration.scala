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

import org.joda.time.DateTimeZone
import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json._
import models.validation.{APIValidation, BaseJsonFormatting, MongoValidation}
import reactivemongo.play.json.BSONFormats.BSONDocumentFormat

import scala.language.implicitConversions


object RegistrationStatus {
  val DRAFT     = "draft"
  val LOCKED    = "locked"
  val HELD      = "held"
  val SUBMITTED = "submitted"
  val REJECTED  = "rejected"
}

case class CorporationTaxRegistration(internalId: String,
                                      registrationID: String,
                                      status: String = RegistrationStatus.DRAFT,
                                      formCreationTimestamp: String,
                                      language: String,
                                      registrationProgress: Option[String] = None,
                                      acknowledgementReferences: Option[AcknowledgementReferences] = None,
                                      confirmationReferences: Option[ConfirmationReferences] = None,
                                      companyDetails: Option[CompanyDetails] = None,
                                      accountingDetails: Option[AccountingDetails] = None,
                                      tradingDetails: Option[TradingDetails] = None,
                                      contactDetails: Option[ContactDetails] = None,
                                      accountsPreparation: Option[AccountPrepDetails] = None,
                                      crn: Option[String] = None,
                                      submissionTimestamp: Option[String] = None,
                                      verifiedEmail: Option[Email] = None,
                                      createdTime: DateTime = CorporationTaxRegistration.now,
                                      lastSignedIn: DateTime = CorporationTaxRegistration.now,
                                      heldTimestamp: Option[DateTime] = None
                                     )

object CorporationTaxRegistration {
  def now: DateTime = DateTime.now(DateTimeZone.UTC)

  def format(formatter: BaseJsonFormatting): Format[CorporationTaxRegistration] = {
    val reads = (
      (__ \ "internalId").read[String] and
      (__ \ "registrationID").read[String] and
      (__ \ "status").read[String] and
      (__ \ "formCreationTimestamp").read[String] and
      (__ \ "language").read[String] and
      (__ \ "registrationProgress").readNullable[String] and
      (__ \ "acknowledgementReferences").readNullable[AcknowledgementReferences](AcknowledgementReferences.format(formatter)) and
      (__ \ "confirmationReferences").readNullable[ConfirmationReferences](ConfirmationReferences.format(formatter)) and
      (__ \ "companyDetails").readNullable[CompanyDetails](CompanyDetails.format(formatter)) and
      (__ \ "accountingDetails").readNullable[AccountingDetails](AccountingDetails.format(formatter)) and
      (__ \ "tradingDetails").readNullable[TradingDetails](TradingDetails.format(formatter)) and
      (__ \ "contactDetails").readNullable[ContactDetails](ContactDetails.format(formatter)) and
      (__ \ "accountsPreparation").readNullable[AccountPrepDetails](AccountPrepDetails.format(formatter)) and
      (__ \ "crn").readNullable[String] and
      (__ \ "submissionTimestamp").readNullable[String] and
      (__ \ "verifiedEmail").readNullable[Email](Email.format(formatter)) and
      (__ \ "createdTime").read[DateTime] and
      (__ \ "lastSignedIn").read[DateTime].orElse(Reads.pure(CorporationTaxRegistration.now)) and
      (__ \ "heldTimestamp").readNullable[DateTime]
    )(CorporationTaxRegistration.apply _)

    val writes = (
      (__ \ "internalId").write[String] and
      (__ \ "registrationID").write[String] and
      (__ \ "status").write[String] and
      (__ \ "formCreationTimestamp").write[String] and
      (__ \ "language").write[String] and
      (__ \ "registrationProgress").writeNullable[String] and
      (__ \ "acknowledgementReferences").writeNullable[AcknowledgementReferences](AcknowledgementReferences.format(formatter)) and
      (__ \ "confirmationReferences").writeNullable[ConfirmationReferences](ConfirmationReferences.format(formatter)) and
      (__ \ "companyDetails").writeNullable[CompanyDetails](CompanyDetails.format(formatter)) and
      (__ \ "accountingDetails").writeNullable[AccountingDetails](AccountingDetails.format(formatter)) and
      (__ \ "tradingDetails").writeNullable[TradingDetails](TradingDetails.format(formatter)) and
      (__ \ "contactDetails").writeNullable[ContactDetails](ContactDetails.format(formatter)) and
      (__ \ "accountsPreparation").writeNullable[AccountPrepDetails](AccountPrepDetails.format(formatter)) and
      (__ \ "crn").writeNullable[String] and
      (__ \ "submissionTimestamp").writeNullable[String] and
      (__ \ "verifiedEmail").writeNullable[Email](Email.format(formatter)) and
      (__ \ "createdTime").write[DateTime] and
      (__ \ "lastSignedIn").write[DateTime] and
      (__ \ "heldTimestamp").writeNullable[DateTime]
    )(unlift(CorporationTaxRegistration.unapply))

    Format(reads, writes)
  }

  def oFormat(format: Format[CorporationTaxRegistration]): OFormat[CorporationTaxRegistration] = {
    new OFormat[CorporationTaxRegistration] {
      override def writes(o: CorporationTaxRegistration): JsObject = format.writes(o).as[JsObject]

      override def reads(json: JsValue): JsResult[CorporationTaxRegistration] = format.reads(json)
    }
  }

  implicit val apiFormat = format(APIValidation)
}

case class AcknowledgementReferences(ctUtr: Option[String],
                                     timestamp: String,
                                     status: String)

object AcknowledgementReferences {
  def format(formatter: BaseJsonFormatting) = {
    val pathCTUtr = formatter match {
      case APIValidation => "ctUtr"
      case MongoValidation => "ct-utr"
    }
    ((__ \ pathCTUtr).formatNullable[String](formatter.cryptoFormat) and
     (__ \ "timestamp").format[String] and
     (__ \ "status").format[String]
    )(AcknowledgementReferences.apply, unlift(AcknowledgementReferences.unapply))
  }

  implicit val apiFormat = format(APIValidation)
}

case class ConfirmationReferences(acknowledgementReference: String = "",
                                  transactionId: String,
                                  paymentReference: Option[String],
                                  paymentAmount: Option[String])

object ConfirmationReferences {
  def format(formatter: BaseJsonFormatting): Format[ConfirmationReferences] = (
    (__ \ "acknowledgement-reference").format[String](formatter.ackRefValidator) and
    (__ \ "transaction-id").format[String] and
    (__ \ "payment-reference").formatNullable[String] and
    (__ \ "payment-amount").formatNullable[String]
  )(ConfirmationReferences.apply, unlift(ConfirmationReferences.unapply))

  implicit val apiFormat = format(APIValidation)
}

case class CompanyDetails(companyName: String,
                          registeredOffice: CHROAddress,
                          ppob: PPOB,
                          jurisdiction: String)

object CompanyDetails {
  def format(formatter: BaseJsonFormatting): Format[CompanyDetails] = (
    (__ \ "companyName").format[String](formatter.companyNameValidator) and
    (__ \ "cHROAddress").format[CHROAddress](CHROAddress.format(formatter)) and
    (__ \ "pPOBAddress").format[PPOB](PPOB.format(formatter)) and
    (__ \ "jurisdiction").format[String]
  )(CompanyDetails.apply, unlift(CompanyDetails.unapply))

  implicit val apiFormat = format(APIValidation)
}

case class CHROAddress(premises: String,
                       address_line_1: String,
                       address_line_2: Option[String],
                       country: String,
                       locality: String,
                       po_box: Option[String],
                       postal_code: Option[String],
                       region: Option[String])

object CHROAddress {
  def format(formatter: BaseJsonFormatting): Format[CHROAddress] = (
    (__ \ "premises").format[String](formatter.chPremisesValidator) and
    (__ \ "address_line_1").format[String](formatter.chLineValidator) and
    (__ \ "address_line_2").formatNullable[String](formatter.chLineValidator) and
    (__ \ "country").format[String](formatter.chLineValidator) and
    (__ \ "locality").format[String](formatter.chLineValidator) and
    (__ \ "po_box").formatNullable[String](formatter.chLineValidator) and
    (__ \ "postal_code").formatNullable[String](formatter.chPostcodeValidator) and
    (__ \ "region").formatNullable[String](formatter.chRegionValidator)
  )(CHROAddress.apply, unlift(CHROAddress.unapply))

  implicit val apiFormat = format(APIValidation)
}

case class PPOBAddress(line1: String,
                       line2: String,
                       line3: Option[String],
                       line4: Option[String],
                       postcode: Option[String],
                       country: Option[String],
                       uprn: Option[String] = None,
                       txid: String)

object PPOBAddress {
  def format(formatter: BaseJsonFormatting): Format[PPOBAddress] = {
    val formatDef = (
      (__ \ "addressLine1").format[String](formatter.lineValidator) and
      (__ \ "addressLine2").format[String](formatter.lineValidator) and
      (__ \ "addressLine3").formatNullable[String](formatter.lineValidator) and
      (__ \ "addressLine4").formatNullable[String](formatter.line4Validator) and
      (__ \ "postCode").formatNullable[String](formatter.postcodeValidator) and
      (__ \ "country").formatNullable[String](formatter.countryValidator) and
      (__ \ "uprn").formatNullable[String] and
      (__ \ "txid").format[String]
    )(PPOBAddress.apply, unlift(PPOBAddress.unapply))

    formatter.ppobAddressFormatWithFilter(formatDef)
  }

  implicit val apiFormat = format(APIValidation)
}

case class PPOB(addressType: String,
                address: Option[PPOBAddress])

object PPOB {
  def format(formatter: BaseJsonFormatting): Format[PPOB] = {
    implicit val formatPPOBAddress: Format[PPOBAddress] = PPOBAddress.format(formatter)
    Json.format[PPOB]
  }

  implicit val apiFormat = format(APIValidation)

  lazy val RO = "RO"
  lazy val LOOKUP = "LOOKUP"
  lazy val MANUAL = "MANUAL"
}

case class CorporationTaxRegistrationRequest(language: String)

object CorporationTaxRegistrationRequest {
  implicit val format = Json.format[CorporationTaxRegistrationRequest]
}

case class ContactDetails(firstName: String,
                          middleName: Option[String],
                          surname: String,
                          phone: Option[String],
                          mobile: Option[String],
                          email: Option[String]) {
}

object ContactDetails {
  def format(formatter: BaseJsonFormatting): Format[ContactDetails] = {
    val formatDef = (
      (__ \ "contactFirstName").format[String](formatter.nameValidator) and
      (__ \ "contactMiddleName").formatNullable[String](formatter.nameValidator) and
      (__ \ "contactSurname").format[String](formatter.nameValidator) and
      (__ \ "contactDaytimeTelephoneNumber").formatNullable[String](formatter.phoneValidator) and
      (__ \ "contactMobileNumber").formatNullable[String](formatter.phoneValidator) and
      (__ \ "contactEmail").formatNullable[String](formatter.emailValidator)
    )(ContactDetails.apply, unlift(ContactDetails.unapply))

    formatter.contactDetailsFormatWithFilter(formatDef)
  }

  implicit val apiFormat = format(APIValidation)
}

case class TradingDetails(regularPayments: String = "")

object TradingDetails {
  def format(formatter: BaseJsonFormatting): Format[TradingDetails] = {
    val boolToStringReads: Reads[String] = new Reads[String] {
      def reads(json: JsValue): JsResult[String] = {
        json match {
          case JsBoolean(true) => JsSuccess("true")
          case JsBoolean(false) => JsSuccess("false")
          case _ => JsError()
        }
      }
    }

    val reads: Reads[TradingDetails] = (__ \ "regularPayments").read[String](boolToStringReads orElse formatter.tradingDetailsValidator).map(p => TradingDetails(p))
    val writes: Writes[TradingDetails] = (__ \ "regularPayments").write[String].contramap(td => td.regularPayments)

    Format(reads, writes)
  }

  implicit val apiFormat = format(APIValidation)
}


case class AccountingDetails(status: String, activeDate: Option[String])

object AccountingDetails {
  val WHEN_REGISTERED = "WHEN_REGISTERED"
  val FUTURE_DATE = "FUTURE_DATE"
  val NOT_PLANNING_TO_YET = "NOT_PLANNING_TO_YET"

  def format(formatter: BaseJsonFormatting): Format[AccountingDetails] = {
    val formatDef = (
      (__ \ "accountingDateStatus").format[String](formatter.acctStatusValidator) and
      (__ \ "startDateOfBusiness").formatNullable[String](formatter.startDateValidator)
    )(AccountingDetails.apply, unlift(AccountingDetails.unapply))

    formatter.accountingDetailsFormatWithFilter(formatDef)
  }

  implicit val apiFormat = format(APIValidation)
}

case class AccountPrepDetails(status: String = AccountPrepDetails.HMRC_DEFINED,
                              endDate: Option[DateTime] = None)

object AccountPrepDetails {
  val HMRC_DEFINED = "HMRC_DEFINED"
  val COMPANY_DEFINED = "COMPANY_DEFINED"

  def format(formatter: BaseJsonFormatting): Format[AccountPrepDetails] = {
    val formatDef = (
      (__ \ "businessEndDateChoice").format[String](formatter.acctPrepStatusValidator) and
      (__ \ "businessEndDate").formatNullable[DateTime](formatter.dateFormat)
    )(AccountPrepDetails.apply, unlift(AccountPrepDetails.unapply))

    formatter.accountPrepDetailsFormatWithFilter(formatDef)
  }

  implicit val apiFormat = format(APIValidation)
}

case class HO6RegistrationInformation(status: String,
                                      companyName: Option[String],
                                      ho5Flag: Option[String])

object HO6RegistrationInformation {
  val writes = (
    (__ \ "status").write[String] and
      (__ \ "companyName").writeNullable[String] and
      (__ \ "registrationProgress").writeNullable[String]
    )(unlift(HO6RegistrationInformation.unapply))
}
