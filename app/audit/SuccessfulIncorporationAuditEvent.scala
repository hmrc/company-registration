/*
 * Copyright 2022 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier

case class SuccessfulIncorporationAuditEventDetail(journeyId: String,
                                                   companyRegistrationNumber: String,
                                                   incorporationDate: DateTime)

object SuccessfulIncorporationAuditEventDetail {
  implicit val writes = new Writes[SuccessfulIncorporationAuditEventDetail] {
    def writes(detail: SuccessfulIncorporationAuditEventDetail) = {
      val dateWrites = Writes[DateTime](
        js =>
          Json.toJson(js.toString("yyyy-MM-dd"))
      )
      val successWrites = (
        (__ \ "journeyId").write[String] and
          (__ \ "companyRegistrationNumber").write[String] and
          (__ \ "incorporationDate").write[DateTime](dateWrites)
        ) (unlift(SuccessfulIncorporationAuditEventDetail.unapply))

      Json.toJson(detail)(successWrites).as[JsObject]
    }
  }
}

class SuccessfulIncorporationAuditEvent(details: SuccessfulIncorporationAuditEventDetail, auditType: String, transactionName: String)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent(auditType, Some(transactionName), Json.toJson(details).as[JsObject], TagSet.REQUEST_ONLY) {
}
