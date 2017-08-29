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

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.maxLength
import play.api.libs.json._
import Validation.withFilter
import auth.Crypto
import models.validation.{APIValidation, BaseJsonFormatting, MongoValidation}

import scala.language.implicitConversions


object RegistrationStatus {
  val DRAFT = "draft"
  val HELD = "held"
  val SUBMITTED = "submitted"
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
                                      lastSignedIn: DateTime = CorporationTaxRegistration.now
                                     )

object CorporationTaxRegistration {

  def now = DateTime.now(DateTimeZone.UTC)

  val formatCH = CHROAddress.format
  implicit val formatPPOBAddress = PPOBAddress.format
  implicit val formatPPOB = PPOB.format
  implicit val formatTD = TradingDetails.format
  val formatCompanyDetails = CompanyDetails.format
  implicit val formatAccountingDetails = AccountingDetails.formats
  implicit val formatContactDetails = ContactDetails.format
  val formatAck = AcknowledgementReferences.apiFormat
  implicit val formatConfirmationReferences = ConfirmationReferences.format
  implicit val formatAccountsPrepDate = AccountPrepDetails.format
  implicit val formatEmail = Email.formatter(APIValidation)

  def formatter(formatter: BaseJsonFormatting) = {
    (
      (__ \ "internalId").format[String] and
        (__ \ "registrationID").format[String] and
        (__ \ "status").format[String] and
        (__ \ "formCreationTimestamp").format[String] and
        (__ \ "language").format[String] and
        (__ \ "registrationProgress").formatNullable[String] and
        (__ \ "acknowledgementReferences").formatNullable[AcknowledgementReferences](AcknowledgementReferences.formatter(formatter)) and
        (__ \ "confirmationReferences").formatNullable[ConfirmationReferences] and
        (__ \ "companyDetails").formatNullable[CompanyDetails](formatCompanyDetails) and
        (__ \ "accountingDetails").formatNullable[AccountingDetails] and
        (__ \ "tradingDetails").formatNullable[TradingDetails] and
        (__ \ "contactDetails").formatNullable[ContactDetails](ContactDetails.formatter(formatter)) and
        (__ \ "accountsPreparation").formatNullable[AccountPrepDetails] and
        (__ \ "crn").formatNullable[String] and
        (__ \ "submissionTimestamp").formatNullable[String] and
        (__ \ "verifiedEmail").formatNullable[Email](Email.formatter(formatter)) and
        (__ \ "createdTime").format[DateTime] and
        (__ \ "lastSignedIn").format[DateTime](formatter.lastSignedInDateTimeFormat)
      ) (CorporationTaxRegistration.apply, unlift(CorporationTaxRegistration.unapply))
  }

  def oFormat(format: Format[CorporationTaxRegistration]): OFormat[CorporationTaxRegistration] = {
    new OFormat[CorporationTaxRegistration] {
      override def writes(o: CorporationTaxRegistration): JsObject = format.writes(o).as[JsObject]

      override def reads(json: JsValue): JsResult[CorporationTaxRegistration] = format.reads(json)
    }
  }

  def cTReads(formatter: BaseJsonFormatting) = {
    (
      (__ \ "internalId").read[String] and
        (__ \ "registrationID").read[String] and
        (__ \ "status").read[String] and
        (__ \ "formCreationTimestamp").read[String] and
        (__ \ "language").read[String] and
        (__ \ "registrationProgress").readNullable[String] and
        (__ \ "acknowledgementReferences").readNullable[AcknowledgementReferences](AcknowledgementReferences.formatter(formatter)) and
        (__ \ "confirmationReferences").readNullable[ConfirmationReferences] and
        (__ \ "companyDetails").readNullable[CompanyDetails](formatCompanyDetails) and
        (__ \ "accountingDetails").readNullable[AccountingDetails] and
        (__ \ "tradingDetails").readNullable[TradingDetails] and
        (__ \ "contactDetails").readNullable[ContactDetails](ContactDetails.formatter(formatter)) and
        (__ \ "accountsPreparation").readNullable[AccountPrepDetails] and
        (__ \ "crn").readNullable[String] and
        (__ \ "submissionTimestamp").readNullable[String] and
        (__ \ "verifiedEmail").readNullable[Email](Email.formatter(formatter)) and
        (__ \ "createdTime").read[DateTime] and
        (__ \ "lastSignedIn").read[DateTime].orElse(Reads.pure(CorporationTaxRegistration.now))
      ) (CorporationTaxRegistration.apply _)
  }

  def cTWrites(wtsAck: Writes[AcknowledgementReferences]): OWrites[CorporationTaxRegistration] = (
    (__ \ "internalId").write[String] and
      (__ \ "registrationID").write[String] and
      (__ \ "status").write[String] and
      (__ \ "formCreationTimestamp").write[String] and
      (__ \ "language").write[String] and
      (__ \ "registrationProgress").writeNullable[String] and
      (__ \ "acknowledgementReferences").writeNullable[AcknowledgementReferences](wtsAck) and
      (__ \ "confirmationReferences").writeNullable[ConfirmationReferences] and
      (__ \ "companyDetails").writeNullable[CompanyDetails](formatCompanyDetails) and
      (__ \ "accountingDetails").writeNullable[AccountingDetails] and
      (__ \ "tradingDetails").writeNullable[TradingDetails] and
      (__ \ "contactDetails").writeNullable[ContactDetails] and
      (__ \ "accountsPreparation").writeNullable[AccountPrepDetails] and
      (__ \ "crn").writeNullable[String] and
      (__ \ "submissionTimestamp").writeNullable[String] and
      (__ \ "verifiedEmail").writeNullable[Email] and
      (__ \ "createdTime").write[DateTime] and
      (__ \ "lastSignedIn").write[DateTime]
    )(unlift(CorporationTaxRegistration.unapply))

  implicit val format: Format[CorporationTaxRegistration] = formatter(APIValidation)
}

case class AcknowledgementReferences(ctUtr: String,
                                     timestamp: String,
                                     status: String)

object AcknowledgementReferences {
  def formatter(formatter: BaseJsonFormatting) = {
    val pathCTUtr = formatter match {
      case APIValidation => "ctUtr"
      case MongoValidation => "ct-utr"
    }

    (
      (__ \ pathCTUtr).format[String](formatter.cryptoFormat) and
      (__ \ "timestamp").format[String] and
      (__ \ "status").format[String]
    ) (AcknowledgementReferences.apply _, unlift(AcknowledgementReferences.unapply _))
  }

  val apiFormat = formatter(APIValidation)
}

case class ConfirmationReferences(acknowledgementReference: String = "",
                                  transactionId: String,
                                  paymentReference: String,
                                  paymentAmount: String)

object ConfirmationReferences {
  implicit val format = (
    (__ \ "acknowledgement-reference").format[String](maxLength[String](31)) and
      (__ \ "transaction-id").format[String] and
      (__ \ "payment-reference").format[String] and
      (__ \ "payment-amount").format[String]
    ) (ConfirmationReferences.apply, unlift(ConfirmationReferences.unapply))
}

case class CompanyDetails(companyName: String,
                          registeredOffice: CHROAddress,
                          ppob: PPOB,
                          jurisdiction: String)

object CompanyDetails extends CompanyDetailsValidator {
  val formatCH = CHROAddress.format
  val formatPPOBAddress = PPOBAddress.format
  val formatPPOB = PPOB.format
  val formatTD = TradingDetails.format
  implicit val format = (
    (__ \ "companyName").format[String](companyNameValidator) and
      (__ \ "cHROAddress").format[CHROAddress](formatCH) and
      (__ \ "pPOBAddress").format[PPOB](formatPPOB) and
      (__ \ "jurisdiction").format[String]
    ) (CompanyDetails.apply, unlift(CompanyDetails.unapply))
}

case class CHROAddress(premises: String,
                       address_line_1: String,
                       address_line_2: Option[String],
                       country: String,
                       locality: String,
                       po_box: Option[String],
                       postal_code: Option[String],
                       region: Option[String])

object CHROAddress extends CHAddressValidator {
  implicit val format = (
    (__ \ "premises").format[String](premisesValidator) and
      (__ \ "address_line_1").format[String](lineValidator) and
      (__ \ "address_line_2").formatNullable[String](lineValidator) and
      (__ \ "country").format[String](lineValidator) and
      (__ \ "locality").format[String](lineValidator) and
      (__ \ "po_box").formatNullable[String](lineValidator) and
      (__ \ "postal_code").formatNullable[String](postcodeValidator) and
      (__ \ "region").formatNullable[String](regionValidator)
    ) (CHROAddress.apply, unlift(CHROAddress.unapply))
}

case class PPOBAddress(line1: String,
                       line2: String,
                       line3: Option[String],
                       line4: Option[String],
                       postcode: Option[String],
                       country: Option[String],
                       uprn: Option[String] = None,
                       txid: String)

object PPOBAddress extends HMRCAddressValidator {
  implicit val format = withFilter(
    ((__ \ "addressLine1").format[String](lineValidator) and
      (__ \ "addressLine2").format[String](lineValidator) and
      (__ \ "addressLine3").formatNullable[String](lineValidator) and
      (__ \ "addressLine4").formatNullable[String](line4Validator) and
      (__ \ "postCode").formatNullable[String](postcodeValidator) and
      (__ \ "country").formatNullable[String](countryValidator) and
      (__ \ "uprn").formatNullable[String] and
      (__ \ "txid").format[String]
    ) (PPOBAddress.apply, unlift(PPOBAddress.unapply)),
      ValidationError("Must have at least one of postcode and country")
    )(
      ppob => ppob.postcode.isDefined || ppob.country.isDefined
    )
}

case class PPOB(addressType: String,
                address: Option[PPOBAddress])

object PPOB {
  implicit val format = Json.format[PPOB]

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
  def formatter(formatter: BaseJsonFormatting) = withFilter(
    ((__ \ "contactFirstName").format[String](formatter.nameValidator) and
      (__ \ "contactMiddleName").formatNullable[String](formatter.nameValidator) and
      (__ \ "contactSurname").format[String](formatter.nameValidator) and
      (__ \ "contactDaytimeTelephoneNumber").formatNullable[String](formatter.phoneValidator) and
      (__ \ "contactMobileNumber").formatNullable[String](formatter.phoneValidator) and
      (__ \ "contactEmail").formatNullable[String](formatter.emailValidator)
      )(ContactDetails.apply, unlift(ContactDetails.unapply)),
    ValidationError("Must have at least one email, phone or mobile specified")
  )(
    cD => cD.mobile.isDefined || cD.phone.isDefined || cD.email.isDefined
  )

  implicit val format = formatter(APIValidation)

  val mongoFormat = formatter(MongoValidation)
}

case class TradingDetails(regularPayments: String = "")

object TradingDetails extends TradingDetailsValidator {

  val boolToStringReads: Reads[String] = new Reads[String] {
    def reads(json: JsValue): JsResult[String] = {
      json match {
        case JsBoolean(true) => JsSuccess("true")
        case JsBoolean(false) => JsSuccess("false")
        case _ => JsError()
      }
    }
  }

  val reads: Reads[TradingDetails] = (__ \ "regularPayments").read[String](boolToStringReads orElse tradingDetailsValidator).map(p => TradingDetails(p))
  val write: Writes[TradingDetails] = (__ \ "regularPayments").write[String].contramap(td => td.regularPayments)

  implicit val format = Format(reads, write)
}


case class AccountingDetails(status: String, activeDate: Option[String])

object AccountingDetails extends AccountingDetailsValidator {
  val WHEN_REGISTERED = "WHEN_REGISTERED"
  val FUTURE_DATE = "FUTURE_DATE"
  val NOT_PLANNING_TO_YET = "NOT_PLANNING_TO_YET"

  implicit val formats = withFilter[AccountingDetails](
    ((__ \ "accountingDateStatus").format[String](statusValidator) and
      (__ \ "startDateOfBusiness").formatNullable[String](startDateValidator)
    ) (AccountingDetails.apply, unlift(AccountingDetails.unapply)),
      ValidationError("If a date is specified, the status must be FUTURE_DATE")
    )(
      aD => if (aD.activeDate.isDefined) aD.status == FUTURE_DATE else aD.status != FUTURE_DATE
    )
}

case class AccountPrepDetails(status: String = AccountPrepDetails.HMRC_DEFINED,
                              endDate: Option[DateTime] = None)

object AccountPrepDetails extends AccountPrepDetailsValidator {
  val HMRC_DEFINED = "HMRC_DEFINED"
  val COMPANY_DEFINED = "COMPANY_DEFINED"

  implicit val format = withFilter[AccountPrepDetails](
    ((__ \ "businessEndDateChoice").format[String](statusValidator) and
      (__ \ "businessEndDate").formatNullable[DateTime](dateFormat)
    ) (AccountPrepDetails.apply, unlift(AccountPrepDetails.unapply)),
      ValidationError("If a date is specified, the status must be COMPANY_DEFINED")
    )(
      aPD => if (aPD.endDate.isDefined) aPD.status == COMPANY_DEFINED else aPD.status != COMPANY_DEFINED
    )
}

case class HO6RegistrationInformation(status: String,
                                      companyName: Option[String],
                                      ho5Flag: Option[String])

object HO6RegistrationInformation {

  val reads = (
    (__ \ "status").read[String] and
      (__ \ "companyDetails" \ "companyName").readNullable[String].orElse(Reads.pure(None)) and
      (__ \ "registrationProgress").readNullable[String]
    )(HO6RegistrationInformation.apply _)

  val writes = (
    (__ \ "status").write[String] and
      (__ \ "companyName").writeNullable[String] and
      (__ \ "registrationProgress").writeNullable[String]
    )(unlift(HO6RegistrationInformation.unapply))
}
