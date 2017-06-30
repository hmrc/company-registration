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

import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.maxLength
import play.api.libs.json._
import Validation.withFilter
import auth.Crypto

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
  implicit val formatEmail = Email.formats

  def cTReads(rdsAck: Reads[AcknowledgementReferences]) = {
    (
      (__ \ "internalId").read[String] and
        (__ \ "registrationID").read[String] and
        (__ \ "status").read[String] and
        (__ \ "formCreationTimestamp").read[String] and
        (__ \ "language").read[String] and
        (__ \ "registrationProgress").readNullable[String] and
        (__ \ "acknowledgementReferences").readNullable[AcknowledgementReferences](rdsAck) and
        (__ \ "confirmationReferences").readNullable[ConfirmationReferences] and
        (__ \ "companyDetails").readNullable[CompanyDetails](formatCompanyDetails) and
        (__ \ "accountingDetails").readNullable[AccountingDetails] and
        (__ \ "tradingDetails").readNullable[TradingDetails] and
        (__ \ "contactDetails").readNullable[ContactDetails] and
        (__ \ "accountsPreparation").readNullable[AccountPrepDetails] and
        (__ \ "crn").readNullable[String] and
        (__ \ "submissionTimestamp").readNullable[String] and
        (__ \ "verifiedEmail").readNullable[Email] and
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

  implicit val format: Format[CorporationTaxRegistration] = Format(cTReads(formatAck), cTWrites(formatAck))
}

case class AcknowledgementReferences(ctUtr: String,
                                     timestamp: String,
                                     status: String)

object AcknowledgementReferences {

  val apiFormat = (
    (__ \ "ctUtr").format[String] and
      (__ \ "timestamp").format[String] and
      (__ \ "status").format[String]
    ) (AcknowledgementReferences.apply _, unlift(AcknowledgementReferences.unapply _))

  def mongoFormat(cryptoRds: Reads[String], cryptoWts: Writes[String]) = (
    (__ \ "ct-utr").format[String](cryptoRds)(cryptoWts) and
      (__ \ "timestamp").format[String] and
      (__ \ "status").format[String]
    ) (AcknowledgementReferences.apply _, unlift(AcknowledgementReferences.unapply _))

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

object ContactDetails extends ContactDetailsValidator {

  implicit val format = withFilter(
    ((__ \ "contactFirstName").format[String](nameValidator) and
      (__ \ "contactMiddleName").formatNullable[String](nameValidator) and
      (__ \ "contactSurname").format[String](nameValidator) and
      (__ \ "contactDaytimeTelephoneNumber").formatNullable[String](phoneValidator) and
      (__ \ "contactMobileNumber").formatNullable[String](phoneValidator) and
      (__ \ "contactEmail").formatNullable[String](emailValidator)
    )(ContactDetails.apply, unlift(ContactDetails.unapply)),
    ValidationError("Must have at least one email, phone or mobile specified")
  )(
    cD => cD.mobile.isDefined || cD.phone.isDefined || cD.email.isDefined
  )
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
