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
                                      confirmationReferences: Option[ConfirmationReferences] = None,
                                      companyDetails: Option[CompanyDetails] = None,
                                      accountingDetails: Option[AccountingDetails] = None,
                                      tradingDetails: Option[TradingDetails] = None,
                                      contactDetails: Option[ContactDetails] = None,
                                      accountsPreparation: Option[PrepareAccountMongoModel] = None
                                     )

object CorporationTaxRegistration {
  implicit val formatCH = Json.format[CHROAddress]
  implicit val formatRO = Json.format[ROAddress]
  implicit val formatPPOB = Json.format[PPOBAddress]
  implicit val formatTD = Json.format[TradingDetails]
  implicit val formatCompanyDetails = Json.format[CompanyDetails]
  implicit val formatAccountingDetails = Json.format[AccountingDetails]
  implicit val formatContactDetails = Json.format[ContactDetails]
  implicit val formatConfirmationReferences = Json.format[ConfirmationReferences]
  implicit val formatAccountsPrepDate = Json.format[PrepareAccountMongoModel]
  implicit val formats = Json.format[CorporationTaxRegistration]
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

case class AccountingDetails(accountingDateStatus : String, startDateOfBusiness : Option[String])

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
                       addressLine2: Option[String],
                       addressLine3: Option[String],
                       addressLine4: Option[String],
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
                          contactEmail: Option[String])

object ContactDetails {
  implicit val formatsLinks = Json.format[Links]
  implicit val formats = Json.format[ContactDetails]
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

case class PrepareAccountMongoModel(businessEndDateChoice : String,
                                    businessEndDate : Option[String])

object PrepareAccountMongoModel {
  implicit val formats = Json.format[PrepareAccountMongoModel]
}

case class PrepareAccountModel(businessEndDateChoice : String,
                               businessEndDate : Option[DateTime]){

  def toMongoModel : PrepareAccountMongoModel = {
    PrepareAccountMongoModel(
      businessEndDateChoice,
      businessEndDate.fold[Option[String]](None)(date => Some(date.toString("yyyy-MM-dd")))
    )
  }
}

object PrepareAccountModel {

  val dateReads: Reads[DateTime] = {
    Reads[DateTime](js => js.validate[String].map(DateTime.parse(_, DateTimeFormat.forPattern("yyyy-MM-dd"))))
  }

  val dateWrites: Writes[DateTime] = new Writes[DateTime] {
    def writes(d: DateTime): JsValue = JsString(d.toString("yyyy-MM-dd"))
  }

  implicit val writes: Writes[PrepareAccountModel] = (
    (__ \ "businessEndDateChoice").write[String] and
    (__ \ "businessEndDate").writeNullable[DateTime](dateWrites)
    )(unlift(PrepareAccountModel.unapply))

  implicit val reads: Reads[PrepareAccountModel] = (
    (__ \ "businessEndDateChoice").read[String] and
    (__ \ "businessEndDate").readNullable[DateTime](dateReads)
    )(PrepareAccountModel.apply _)

  def empty = PrepareAccountModel("", None)
}
