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

  def formatTimestamp(ts: DateTime): String = datetime.print(ts)

}

object BusinessType {
  val LimitedCompany = "Limited company"
}

sealed trait CompletionCapacity {
  def text: String
}

object CompletionCapacity {
  implicit val writes = new Writes[CompletionCapacity] {
    def writes(cc: CompletionCapacity) = JsString(cc.text)
  }

  def apply(text: String): CompletionCapacity = text match {
    case Director.text => Director
    case Agent.text => Agent
    case _ => Other(text)
  }
}

case object Director extends CompletionCapacity {
  val text = "Director"
}

case object Agent extends CompletionCapacity {
  val text = "Agent"
}

case class Other(text: String) extends CompletionCapacity

case class BusinessAddress(
                            line1: String,
                            line2: String,
                            line3: Option[String],
                            line4: Option[String],
                            postcode: Option[String],
                            country: Option[String]
                          )

case class BusinessContactDetails(
                                   phoneNumber: Option[String],
                                   mobileNumber: Option[String],
                                   email: Option[String]
                                 )

case class BusinessContactName(
                                firstName: String,
                                middleNames: Option[String],
                                lastName: String
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
    def writes(m: Metadata) = {
      Json.obj(
        "businessType" -> BusinessType.LimitedCompany,
        "submissionFromAgent" -> false,
        "declareAccurateAndComplete" -> true,
        "sessionId" -> m.sessionId,
        "credentialId" -> m.credId,
        "language" -> m.language,
        "formCreationTimestamp" -> formatTimestamp(m.submissionTs)
      ) ++ (
        m.completionCapacity match {
          case Other(cc) => {
            Json.obj(
              "completionCapacity" -> "Other",
              "completionCapacityOther" -> cc
            )
          }
          case _ => Json.obj("completionCapacity" -> m.completionCapacity)
        }
        )
    }
  }
}

case class InterimCorporationTax(
                                  companyName: String,
                                  returnsOnCT61: Boolean,
                                  businessAddress: BusinessAddress,
                                  businessContactName: BusinessContactName,
                                  businessContactDetails: BusinessContactDetails
                                )

object InterimCorporationTax {

  implicit val writes = new Writes[InterimCorporationTax] {
    def writes(m: InterimCorporationTax) = {
      Json.obj(
        "companyOfficeNumber" -> "001", // TODO SCRS-2283 check default value
        "hasCompanyTakenOverBusiness" -> false,
        "companyMemberOfGroup" -> false,
        "companiesHouseCompanyName" -> m.companyName,
        "returnsOnCT61" -> m.returnsOnCT61,
        "companyACharity" -> false,
        "businessAddress" -> Json.obj(
          "line1" -> m.businessAddress.line1,
          "line2" -> m.businessAddress.line2,
          "line3" -> m.businessAddress.line3,
          "line4" -> m.businessAddress.line4,
          "postcode" -> m.businessAddress.postcode,
          "country" -> m.businessAddress.country
        ),
        "businessContactName" -> Json.obj(
          "firstName" -> m.businessContactName.firstName,
          "middleNames" -> m.businessContactName.middleNames,
          "lastName" -> m.businessContactName.lastName
        ),
        "businessContactDetails" -> Json.obj(
          "phoneNumber" -> m.businessContactDetails.phoneNumber,
          "mobileNumber" -> m.businessContactDetails.mobileNumber,
          "email" -> m.businessContactDetails.email
        )
      )

    }
  }
}


case class InterimDesRegistration(ackRef: String, metadata: Metadata, interimCorporationTax: InterimCorporationTax)

object InterimDesRegistration {
  implicit val writes: Writes[InterimDesRegistration] = (
    (__ \ "acknowledgementReference").write[String] and
      (__ \ "registration" \ "metadata").write[Metadata] and
      (__ \ "registration" \ "corporationTax").write[InterimCorporationTax]
    ) (unlift(InterimDesRegistration.unapply))
}