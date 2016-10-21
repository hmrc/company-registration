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

import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.language.implicitConversions

object RegistrationStatus {
  val DRAFT = "draft"
  val HELD = "held"
}

case class CorporationTaxRegistration(OID: String,
                                      registrationID: String,
                                      status: String = RegistrationStatus.DRAFT,
                                      formCreationTimestamp: String,
                                      language: String,
                                      confirmationReferences: Option[ConfirmationReferences] = None,
                                      companyDetails: Option[CompanyDetails] = None,
                                      accountingDetails: Option[AccountingDetails] = None,
                                      tradingDetails: Option[TradingDetails] = None,
                                      contactDetails: Option[ContactDetails] = None) {

  def toCTRegistrationResponse = {
    CorporationTaxRegistrationResponse(
      registrationID,
      status,
      formCreationTimestamp,
      Links(Some(s"/corporation-tax-registration/$registrationID"))
    )
  }
}

object CorporationTaxRegistration {
  implicit val formatCH = Json.format[CHROAddress]
  implicit val formatRO = Json.format[ROAddress]
  implicit val formatPPOB = Json.format[PPOBAddress]
  implicit val formatTD = Json.format[TradingDetails]
  implicit val formatCompanyDetails = Json.format[CompanyDetails]
  implicit val formatAccountingDetails = Json.format[AccountingDetails]
  implicit val formatContactDetails = Json.format[ContactDetails]
  implicit val formatConfirmationReferences = Json.format[ConfirmationReferences]
  implicit val formats = Json.format[CorporationTaxRegistration]
}

case class CorporationTaxRegistrationResponse(registrationID: String,
                                              status: String,
                                              formCreationTimestamp: String,
                                              link: Links)

object CorporationTaxRegistrationResponse {
  implicit val linksFormats = Json.format[Links]
  implicit val formats = Json.format[CorporationTaxRegistrationResponse]
}

case class ConfirmationReferences(
                                   acknowledgementReference: String = "",
                                   transactionId: String,
                                   paymentReference: String,
                                   paymentAmount: String
                                 )
object ConfirmationReferences {
  implicit val format = (
      ( __ \ "acknowledgement-reference" ).format[String] and
      ( __ \ "transaction-id" ).format[String] and
      ( __ \ "payment-reference" ).format[String] and
      ( __ \ "payment-amount" ).format[String]
    )(ConfirmationReferences.apply, unlift(ConfirmationReferences.unapply))
}

case class AccountingDetailsResponse(accountingDateStatus : String,
                             startDateOfBusiness : Option[String],
                             links : Links){
}

object AccountingDetailsResponse {
  implicit val linksFormats = Json.format[Links]
  implicit val formats = Json.format[AccountingDetailsResponse]
}

case class AccountingDetails(accountingDateStatus : String,
                             startDateOfBusiness : Option[String]){

  def toAccountingDetailsResponse(registrationID: String): AccountingDetailsResponse = {
    AccountingDetailsResponse(
      accountingDateStatus,
      startDateOfBusiness,
      Links.buildLinks(registrationID)
    )
  }
}

object AccountingDetails {
  implicit val formats = Json.format[AccountingDetails]
}

case class CompanyDetails(companyName: String,
                          cHROAddress: CHROAddress,
                          rOAddress: ROAddress,
                          pPOBAddress: PPOBAddress,
                          jurisdiction: String)

case class CHROAddress(premises : String,
                       address_line_1 : String,
                       address_line_2 : Option[String],
                       country : String,
                       locality : String,
                       po_box : Option[String],
                       postal_code : Option[String],
                       region : Option[String])

case class ROAddress(houseNameNumber: String,
                     addressLine1: String,
                     addressLine2: String,
                     addressLine3: String,
                     addressLine4: String,
                     postCode: String,
                     country: String)

case class PPOBAddress(houseNameNumber: String,
                       addressLine1: String,
                       addressLine2: String,
                       addressLine3: String,
                       addressLine4: String,
                       postCode: String,
                       country: String)

object CompanyDetails {
  implicit val formatCH = Json.format[CHROAddress]
  implicit val formatRO = Json.format[ROAddress]
  implicit val formatPPOB = Json.format[PPOBAddress]
  implicit val formatTD = Json.format[TradingDetails]
  implicit val formats = Json.format[CompanyDetails]
}

object CHROAddress {
  implicit val format = Json.format[CHROAddress]
}

object ROAddress {
  implicit val format = Json.format[ROAddress]
}

object PPOBAddress {
  implicit val format = Json.format[PPOBAddress]
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
                          contactEmail: Option[String]){

  implicit def convertToResponse(registrationID: String): ContactDetailsResponse = {
    ContactDetailsResponse(contactFirstName, contactMiddleName, contactSurname, contactDaytimeTelephoneNumber, contactMobileNumber, contactEmail,
      Links(Some(s"/corporation-tax-registration/$registrationID/trading-details"), Some(s"/corporation-tax-registration/$registrationID/")))
  }
}

object ContactDetails {
  implicit val formatsLinks = Json.format[Links]
  implicit val formats = Json.format[ContactDetails]
}

case class ContactDetailsResponse(contactFirstName: Option[String],
                                  contactMiddleName: Option[String],
                                  contactSurname: Option[String],
                                  contactDaytimeTelephoneNumber: Option[String],
                                  contactMobileNumber: Option[String],
                                  contactEmail: Option[String],
                                  links: Links)

object ContactDetailsResponse {
  implicit val formatsLinks = Json.format[Links]
  implicit val formats = Json.format[ContactDetailsResponse]
}

case class Links(self: Option[String],
                 registration: Option[String] = None)

object Links {
  implicit val format = Json.format[Links]

  def buildLinks(registrationID: String): Links = {
    Links(
      self = Some(s"/company-registration/corporation-tax-registration/$registrationID/company-details"),
      registration = Some(s"/company-registration/corporation-tax-registration/$registrationID")
    )
  }
}

case class TradingDetails(regularPayments : Boolean = false)

object TradingDetails {
  implicit val format = Json.format[TradingDetails]
}
