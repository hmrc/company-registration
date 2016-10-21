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

package models.des

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._
import play.api.libs.json.Writes._
import play.api.libs.functional.syntax._

object DesFormats {

  private val datetime = ISODateTimeFormat.dateTime()

  def formatTimestamp( ts: DateTime ) : String = datetime.print(ts)

}

case class InterimDesRegistration(ackRef: String, wibble: String = "xxx")

object InterimDesRegistration {
  implicit val format = (
    (__ \ "acknowledgementReference").format[String] and
    (__ \ "wibble").format[String]
    )(InterimDesRegistration.apply, unlift(InterimDesRegistration.unapply))
}

object BusinessType {
  val LimitedCompany = "Limited company"
}

sealed trait CompletionCapacity { def text : String }

object CompletionCapacity {
  implicit val writes = new Writes[CompletionCapacity] {
    def writes( cc: CompletionCapacity ) = JsString(cc.text)
  }
}

case object Director extends CompletionCapacity { val text = "Director" }
case object Agent extends CompletionCapacity { val text = "Agent" }
case class Other(text: String) extends CompletionCapacity

case class BusinessContactDetails(
                      phoneNumber : Option[String],
                      mobileNumber : Option[String],
                      email : Option[String]
                                 )

case class BusinessContactName(
                                firstName : String,
                                middleNames : Option[String],
                                lastName: Option[String]
                              )

case class Metadata(
                     sessionId: String,
                     credId: String,
                     language: String,
                     submissionTs: DateTime,
                     completionCapacity: CompletionCapacity
                   )

object Metadata {

  import DesFormats._

  implicit val writes = new Writes[Metadata] {
    def writes( m: Metadata ) = {
      Json.obj(
        "businessType" -> BusinessType.LimitedCompany,
        "submissionFromAgent" -> false,
        "declareAccurateAndComplete" -> true,
        "sessionId" -> m.sessionId,
        "credentialId" -> m.credId,
        "language" -> m.language,
        "formCreationTimestamp" -> formatTimestamp( m.submissionTs )
      ) ++ (
        m.completionCapacity match {
          case Other(cc) => {
            Json.obj(
              "completionCapacity" -> "Other",
              "completionCapacityOther" -> cc
            ) }
          case _ => Json.obj( "completionCapacity" -> m.completionCapacity )
        }
      )
    }
  }
}

case class InterimCorporationTax(
                      companyActiveDate : String,
                      companiesHouseCompanyName : String,
                      crn : String,
                      startDateOfFirstAccountingPeriod : String,
                      intendedAccountsPreparationDate : String,
                      returnsOnCT61 : String,
                      businessAddress : String,
                      businessContactName : BusinessContactName,
                      businessContactDetails : BusinessContactDetails
                                 )
object InterimCorporationTax {

  import DesFormats._

  implicit val writes = new Writes[InterimCorporationTax] {
    def writes(m: InterimCorporationTax) = {
      Json.obj(
        "companyActiveDate" -> m.companyActiveDate,
        "companiesHouseCompanyName" -> m.companiesHouseCompanyName,
        "crn" -> m. crn,
        "startDateOfFirstAccountingPeriod" -> m.startDateOfFirstAccountingPeriod,
        "intendedAccountsPreparationDate" -> m.intendedAccountsPreparationDate,
        "returnsOnCT61" -> m.returnsOnCT61,
        "businessAddress" -> m.businessAddress,
        "businessContactName" ->  Json.obj(
          "firstName" -> m.businessContactName.firstName,
          "middleNames" -> m.businessContactName.middleNames,
          "lastName" -> m.businessContactName.lastName),
        "businessContactDetails" -> Json.obj(
          "phoneNumber" ->  m.businessContactDetails.phoneNumber,
          "mobileNumber" -> m.businessContactDetails.mobileNumber,
          "email" -> m.businessContactDetails.email
        )
      )
    }
  }
}