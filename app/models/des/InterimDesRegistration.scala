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

object BusinessAddress {
  implicit val writes: Writes[BusinessAddress] = (
    (__ \ "line1").write[String] and
      (__ \ "line2").write[String] and
      (__ \ "line3").writeNullable[String] and
      (__ \ "line4").writeNullable[String] and
      (__ \ "postcode").writeNullable[String] and
      (__ \ "country").writeNullable[String]
    ) (unlift(BusinessAddress.unapply))
}

case class BusinessContactDetails(
                                   phoneNumber: Option[String],
                                   mobileNumber: Option[String],
                                   email: Option[String]
                                 )

object BusinessContactDetails {
  implicit val writes: Writes[BusinessContactDetails] = (
    (__ \ "phoneNumber").writeNullable[String] and
      (__ \ "mobileNumber").writeNullable[String] and
      (__ \ "email").writeNullable[String]
    ) (unlift(BusinessContactDetails.unapply))
}

case class BusinessContactName(
                                firstName: String,
                                middleNames: Option[String],
                                lastName: String
                              )

object BusinessContactName {
  implicit val writes: Writes[BusinessContactName] = (
    (__ \ "firstName").write[String] and
      (__ \ "middleNames").writeNullable[String] and
      (__ \ "lastName").write[String]
    ) (unlift(BusinessContactName.unapply))
}

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
                                  businessAddress: Option[BusinessAddress],
                                  businessContactName: BusinessContactName,
                                  businessContactDetails: BusinessContactDetails
                                )

object InterimCorporationTax {
  implicit val writes = new Writes[InterimCorporationTax] {
    def writes(m: InterimCorporationTax) = {
      val address = Json.toJson(m.businessAddress).as[Option[JsObject]]
      val name: JsObject = Json.toJson(m.businessContactName).as[JsObject]
      val contactDetails: JsObject = Json.toJson(m.businessContactDetails).as[JsObject]
      Json.obj(
        "companyOfficeNumber" -> "623",
        "hasCompanyTakenOverBusiness" -> false,
        "companyMemberOfGroup" -> false,
        "companiesHouseCompanyName" -> m.companyName,
        "returnsOnCT61" -> m.returnsOnCT61,
        "companyACharity" -> false
      ) ++
        address.fold(Json.obj())(add => Json.obj("businessAddress" -> add)) ++
        Json.obj("businessContactName" -> name) ++
        Json.obj("businessContactDetails" -> contactDetails)
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
