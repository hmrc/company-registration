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

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{maxLength, minLength}
import play.api.libs.json._

import scala.language.implicitConversions


object RegistrationStatus {
  val DRAFT = "draft"
  val HELD = "held"
  val SUBMITTED = "submitted"
}

case class CorporationTaxRegistration(OID: String,
                                      registrationID: String,
                                      status: String = RegistrationStatus.DRAFT,
                                      formCreationTimestamp: String,
                                      language: String,
                                      acknowledgementReferences: Option[AcknowledgementReferences] = None,
                                      confirmationReferences: Option[ConfirmationReferences] = None,
                                      companyDetails: Option[CompanyDetails] = None,
                                      accountingDetails: Option[AccountingDetails] = None,
                                      tradingDetails: Option[TradingDetails] = None,
                                      contactDetails: Option[ContactDetails] = None,
                                      accountsPreparation: Option[AccountPrepDetails] = None,
                                      crn: Option[String] = None,
                                      submissionTimestamp: Option[String] = None,
                                      verifiedEmail: Option[Email] = None
                                     )

object CorporationTaxRegistration {
  implicit val formatCH = CHROAddress.format
  implicit val formatRO = ROAddress.format
  implicit val formatPPOB = PPOBAddress.format
  implicit val formatTD = TradingDetails.format
  implicit val formatCompanyDetails = CompanyDetails.format
  implicit val formatAccountingDetails = AccountingDetails.formats
  implicit val formatContactDetails = ContactDetails.format
  implicit val formatAck = AcknowledgementReferences.format
  implicit val formatConfirmationReferences = ConfirmationReferences.format
  implicit val formatAccountsPrepDate = AccountPrepDetails.format
  implicit val formatEmail = Email.formats
  implicit val format = Json.format[CorporationTaxRegistration]
}


case class AcknowledgementReferences(ctUtr : String,
                                     timestamp : String,
                                     status : String)

object AcknowledgementReferences {
  implicit val format = Json.format[AcknowledgementReferences]
}

case class ConfirmationReferences(acknowledgementReference: String = "",
                                  transactionId: String,
                                  paymentReference: String,
                                  paymentAmount: String)

object ConfirmationReferences {
  implicit val format = (
      ( __ \ "acknowledgement-reference" ).format[String](maxLength[String](31)) and
      ( __ \ "transaction-id" ).format[String] and
      ( __ \ "payment-reference" ).format[String] and
      ( __ \ "payment-amount" ).format[String]
    )(ConfirmationReferences.apply, unlift(ConfirmationReferences.unapply))
}

case class CompanyDetails(companyName: String,
                          cHROAddress: CHROAddress,
                          rOAddress: ROAddress,
                          pPOBAddress: PPOBAddress,
                          jurisdiction: String)

object CompanyDetails {
  implicit val formatCH = CHROAddress.format
  implicit val formatRO = ROAddress.format
  implicit val formatPPOB = PPOBAddress.format
  implicit val formatTD = TradingDetails.format
  implicit val format = (
    ( __ \ "companyName" ).format[String](maxLength[String](160)) and
      ( __ \ "cHROAddress" ).format[CHROAddress] and
      ( __ \ "rOAddress" ).format[ROAddress] and
      ( __ \ "pPOBAddress" ).format[PPOBAddress] and
      ( __ \ "jurisdiction" ).format[String]
    )(CompanyDetails.apply, unlift(CompanyDetails.unapply))
}

case class CHROAddress(premises : String,
                       address_line_1 : String,
                       address_line_2 : Option[String],
                       country : String,
                       locality : String,
                       po_box : Option[String],
                       postal_code : Option[String],
                       region : Option[String])

object CHROAddress extends CHAddressValidator {
  implicit val format = (
    (__ \ "premises").format[String](premisesValidator) and
      (__ \ "address_line_1").format[String](lineValidator) and
      (__ \ "address_line_2").formatNullable[String](lineValidator) and
      (__ \ "country").format[String](lineValidator) and
      (__ \ "locality").format[String](lineValidator) and
      (__ \ "po_box").formatNullable[String](lineValidator) and
      (__ \ "postal_code").formatNullable[String](postcodeValidator) and
      (__ \ "region").formatNullable[String]
    )(CHROAddress.apply, unlift(CHROAddress.unapply))
}

case class ROAddress(houseNameNumber: String,
                     addressLine1: String,
                     addressLine2: String,
                     addressLine3: String,
                     addressLine4: String,
                     postCode: String,
                     country: String)

object ROAddress {
  implicit val format = Json.format[ROAddress]
}

case class PPOBAddress(houseNameNumber: String,
                       addressLine1: String,
                       addressLine2: Option[String],
                       addressLine3: Option[String],
                       addressLine4: Option[String],
                       postCode: Option[String],
                       country: Option[String]) {
  require( postCode.isDefined || country.isDefined, "Must have at least one of postcode and country")
}

object PPOBAddress extends HMRCAddressValidator {
  implicit val format = (
    ( __ \ "houseNameNumber").format[String](lineValidator) and
      ( __ \ "addressLine1").format[String](lineValidator) and
      ( __ \ "addressLine2").formatNullable[String](lineValidator) and
      ( __ \ "addressLine3").formatNullable[String](lineValidator) and
      ( __ \ "addressLine4").formatNullable[String](lineValidator) and
      ( __ \ "postCode").formatNullable[String](postcodeValidator) and
      ( __ \ "country").formatNullable[String](countryValidator)
    )(PPOBAddress.apply, unlift(PPOBAddress.unapply))
}

case class CorporationTaxRegistrationRequest(language: String)

object CorporationTaxRegistrationRequest{
  implicit val format = Json.format[CorporationTaxRegistrationRequest]
}

case class ContactDetails(contactFirstName: Option[String],
                          contactMiddleName: Option[String],
                          contactSurname: Option[String],
                          contactDaytimeTelephoneNumber: Option[String],
                          contactMobileNumber: Option[String],
                          contactEmail: Option[String])

object ContactDetails {
  implicit val formatsLinks = Links.format
  implicit val format = Json.format[ContactDetails]
}

case class Links(self: Option[String],
                 registration: Option[String] = None)

object Links {
  implicit val format = Json.format[Links]
}

case class TradingDetails(regularPayments : Boolean = false)

object TradingDetails {
  implicit val format = Json.format[TradingDetails]
}


case class AccountingDetails(status : String, startDateOfBusiness : Option[String]) {
  import AccountingDetails.FUTURE_DATE
  def crossCheck(): Boolean = if (startDateOfBusiness.isDefined) status == FUTURE_DATE else status != FUTURE_DATE

  require(crossCheck, "If a date is specified, the status must be FUTURE_DATE")
}

object AccountingDetails extends AccountingDetailsValidator {
  val WHEN_REGISTERED = "WHEN_REGISTERED"
  val FUTURE_DATE = "FUTURE_DATE"
  val NOT_PLANNING_TO_YET = "NOT_PLANNING_TO_YET"

  implicit val formats = (
      ( __ \ "accountingDateStatus" ).format[String](statusValidator) and
      ( __ \ "startDateOfBusiness").formatNullable[String](startDateValidator)
    )(AccountingDetails.apply, unlift(AccountingDetails.unapply))
}

case class AccountPrepDetails(businessEndDateChoice : String,
                              businessEndDate : Option[DateTime]){
  import AccountPrepDetails.COMPANY_DEFINED
  def crossCheck(): Boolean = if (businessEndDate.isDefined) businessEndDateChoice == COMPANY_DEFINED else businessEndDateChoice != COMPANY_DEFINED

  require(crossCheck, "If a date is specified, the status must be COMPANY_DEFINED")
}

object AccountPrepDetails extends AccountPrepDetailsValidator {
  val HMRC_DEFINED = "HMRC_DEFINED"
  val COMPANY_DEFINED = "COMPANY_DEFINED"

  implicit val format = (
    (__ \ "businessEndDateChoice").format[String](statusValidator) and
    (__ \ "businessEndDate").formatNullable[DateTime](dateFormat)
    )(AccountPrepDetails.apply, unlift(AccountPrepDetails.unapply))
}
