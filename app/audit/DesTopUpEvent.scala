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

import models.SubmissionDates
import models.des.{BusinessAddress, BusinessContactDetails}
import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.mvc.{AnyContent, Request}
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier

case class DesTopUpSubmissionEventDetail(journeyId: String,
                                         acknowledgementReference: String,
                                         incorporationStatus: String,
                                         intendedAccountsPreparationDate: Option[DateTime],
                                         startDateOfFirstAccountingPeriod: Option[DateTime],
                                         companyActiveDate: Option[DateTime],
                                         crn: Option[String])

object DesTopUpSubmissionEventDetail {

  implicit val writes = new Writes[DesTopUpSubmissionEventDetail] {
    def writes(detail: DesTopUpSubmissionEventDetail) = {
            val dateWrites = Writes[DateTime](
              js =>
                Json.toJson(js.toString("yyyy-MM-dd"))
            )
            val successWrites = (
                (__ \ "journeyId").write[String] and
                (__ \ "acknowledgementReference").write[String] and
                (__ \ "incorporationStatus").write[String] and
                (__ \ "intendedAccountsPreparationDate").writeNullable[DateTime](dateWrites) and
                (__ \ "startDateOfFirstAccountingPeriod").writeNullable[DateTime](dateWrites) and
                (__ \ "companyActiveDate").writeNullable[DateTime](dateWrites) and
                (__ \ "crn").writeNullable[String]
              )(unlift(DesTopUpSubmissionEventDetail.unapply))

            Json.toJson(detail)(successWrites).as[JsObject]
          }
        }
      }

      class DesTopUpSubmissionEvent(details: DesTopUpSubmissionEventDetail, auditType : String, transactionName : String)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent(auditType, Some(transactionName), Json.toJson(details).as[JsObject], TagSet.REQUEST_ONLY)