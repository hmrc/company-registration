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

package audit

import models.des.{BusinessAddress, BusinessContactDetails}
import play.api.libs.json.{JsObject, Json, Writes}
import uk.gov.hmrc.play.http.HeaderCarrier

case class DesSubmissionAuditEventDetail(regId: String,
                                         authProviderId: String,
                                         jsSubmission: JsObject)

object DesSubmissionAuditEventDetail {

  import RegistrationAuditEvent.{JOURNEY_ID, ACK_REF, REG_METADATA, CORP_TAX}

  implicit val writes = new Writes[DesSubmissionAuditEventDetail] {
    def writes(detail: DesSubmissionAuditEventDetail) = {

      val address = (detail.jsSubmission \ "registration" \ "corporationTax" \ "businessAddress").
        asOpt[BusinessAddress].fold { Json.obj() } {
        address => Json.obj("businessAddress" -> Json.toJson(address).as[JsObject])
      }

      val contactDetails = (detail.jsSubmission \ "registration" \ "corporationTax" \ "businessContactDetails").
        asOpt[BusinessContactDetails].fold { Json.obj() } {
        contact => Json.obj("businessContactDetails" -> Json.toJson(contact).as[JsObject])
      }

      Json.obj(
        JOURNEY_ID -> detail.regId,
        ACK_REF -> (detail.jsSubmission \ "acknowledgementReference"),
        REG_METADATA -> (detail.jsSubmission \ "registration" \ "metadata").as[JsObject].++(
          Json.obj("authProviderId" -> detail.authProviderId)
        ).-("sessionId").-("credentialId"),
        CORP_TAX -> (detail.jsSubmission \ "registration" \ "corporationTax").as[JsObject].++
        ( address ).++
        ( contactDetails )
      )
    }
  }
}

class DesSubmissionEvent(details: DesSubmissionAuditEventDetail)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("ctRegistrationSubmission", None, Json.toJson(details).as[JsObject])(hc)
