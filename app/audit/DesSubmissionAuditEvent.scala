/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.{JsObject, JsString, Json, Writes}
import RegistrationAuditEvent.JOURNEY_ID
import uk.gov.hmrc.http.HeaderCarrier

case class DesSubmissionAuditEventDetail(regId: String,
                                         jsSubmission: JsObject)

object DesSubmissionAuditEventDetail {

  import RegistrationAuditEvent.{ACK_REF, REG_METADATA, CORP_TAX}

  implicit val writes = new Writes[DesSubmissionAuditEventDetail] {
    def writes(detail: DesSubmissionAuditEventDetail) = {

      Json.obj(
        JOURNEY_ID -> detail.regId,
        ACK_REF -> (detail.jsSubmission \ "acknowledgementReference").as[JsString],
        REG_METADATA -> (detail.jsSubmission \ "registration" \ "metadata").as[JsObject]
        .-("sessionId").-("credentialId"),
        CORP_TAX -> (detail.jsSubmission \ "registration" \ "corporationTax").as[JsObject]
      )
    }
  }
}

class DesSubmissionEvent(details: DesSubmissionAuditEventDetail,isAdmin: Boolean = false)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("ctRegistrationSubmission", None, Json.toJson(details).as[JsObject], if(isAdmin)TagSet.REQUEST_ONLY_WITH_ADMIN else TagSet.REQUEST_ONLY)(hc)

class DesSubmissionEventFailure(regId: String, details: JsObject)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent("ctRegistrationSubmissionFailed", None, Json.obj("submission" -> details, JOURNEY_ID -> regId))(hc)
