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

import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate

case class DesTopUpSubmissionEventDetail(journeyId: String,
                                         acknowledgementReference: String,
                                         incorporationStatus: String,
                                         intendedAccountsPreparationDate: Option[LocalDate],
                                         startDateOfFirstAccountingPeriod: Option[LocalDate],
                                         companyActiveDate: Option[LocalDate],
                                         crn: Option[String],
                                         rejectedAsNotPaid: Option[Boolean] = None)

object DesTopUpSubmissionEventDetail {
  implicit val writes = Json.writes[DesTopUpSubmissionEventDetail]
}

class DesTopUpSubmissionEvent(details: DesTopUpSubmissionEventDetail, auditType: String, transactionName: String)(implicit hc: HeaderCarrier)
  extends RegistrationAuditEvent(auditType, Some(transactionName), Json.toJson(details).as[JsObject], TagSet.REQUEST_ONLY)
