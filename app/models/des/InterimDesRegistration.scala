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

package models.des

import java.text.Normalizer
import java.text.Normalizer.Form

import models.CompanyDetailsValidator
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.Logger
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

  def apply(text: String): CompletionCapacity = text.toLowerCase match {
    case d if d == Director.text.toLowerCase => Director
    case a if a == Agent.text.toLowerCase => Agent
    case _ => Other(text)
  }
}

case object Director extends CompletionCapacity {
  val text = "Director"
}

case object Agent extends CompletionCapacity {
  val text = "Agent"
}

case class Other(txt: String) extends CompletionCapacity {
  val text = txt
}

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

  implicit val reads: Reads[BusinessAddress] = (
    (__ \ "line1").read[String] and
      (__ \ "line2").read[String] and
      (__ \ "line3").readNullable[String] and
      (__ \ "line4").readNullable[String] and
      (__ \ "postcode").readNullable[String] and
      (__ \ "country").readNullable[String]
    ) (BusinessAddress.apply _)

  def auditWrites(addressTransId: Option[String], entryMethod: String, uprn: Option[String], address: BusinessAddress): Writes[BusinessAddress] = {
    val aWrites: OWrites[BusinessAddress] = (
      (__ \ "addressLine1").write[String] and
        (__ \ "addressLine2").write[String] and
        (__ \ "addressLine3").writeNullable[String] and
        (__ \ "addressLine4").writeNullable[String] and
        (__ \ "postCode").writeNullable[String] and
        (__ \ "country").writeNullable[String]
      ) (unlift(BusinessAddress.unapply))

    new Writes[BusinessAddress] {
      def writes(m: BusinessAddress) = {
        Json.obj(
          "addressEntryMethod" -> entryMethod
        ).++(
          addressTransId.fold[JsObject](Json.obj())(id => Json.obj("transactionId" -> id))
        ).++(
          Json.toJson(address)(aWrites).as[JsObject]
        ).++(
          uprn.fold[JsObject](Json.obj())(_ => Json.obj("uprn" -> uprn))
        )
      }
    }
  }
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

  implicit val reads: Reads[BusinessContactDetails] = (
    (__ \ "phoneNumber").readNullable[String] and
      (__ \ "mobileNumber").readNullable[String] and
      (__ \ "email").readNullable[String]
    ) (BusinessContactDetails.apply _)

  def auditWrites(details: BusinessContactDetails): Writes[BusinessContactDetails] = {

    val aWrites: OWrites[BusinessContactDetails] = (
      (__ \ "telephoneNumber").writeNullable[String] and
        (__ \ "mobileNumber").writeNullable[String] and
        (__ \ "emailAddress").writeNullable[String]
      ) (unlift(BusinessContactDetails.unapply))

    new Writes[BusinessContactDetails] {
      def writes(m: BusinessContactDetails) = {
        Json.toJson(m)(aWrites).as[JsObject]
      }
    }
  }
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

object InterimCorporationTax extends CompanyDetailsValidator {

  implicit val writes = new Writes[InterimCorporationTax] {
    def writes(m: InterimCorporationTax) = {
      val address = m.businessAddress map {Json.toJson(_).as[JsObject]}
      val name: JsObject = Json.toJson(m.businessContactName).as[JsObject]
      val contactDetails: JsObject = Json.toJson(m.businessContactDetails).as[JsObject]
      Json.obj(
        "companyOfficeNumber" -> "623",
        "hasCompanyTakenOverBusiness" -> false,
        "companyMemberOfGroup" -> false,
        "companiesHouseCompanyName" -> cleanseCompanyName(m.companyName,illegalCharacters),
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
