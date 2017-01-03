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

package services

import audit.{CTRegistrationAuditEvent, CTRegistrationSubmissionAuditEventDetails, DesResponse}
import config.MicroserviceAuditConnector
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AuditService extends AuditService {
  val auditConnector = MicroserviceAuditConnector
}

trait AuditService {

  val auditConnector : AuditConnector

  def sendCTRegSubmissionEvent(event : CTRegistrationAuditEvent)(implicit hc : HeaderCarrier) : Future[AuditResult] = {
    auditConnector.sendEvent(event)
  }

  def buildCTRegSubmissionEvent(detail: CTRegistrationSubmissionAuditEventDetails)(implicit hc : HeaderCarrier) : CTRegistrationAuditEvent = {
    val auditTypeAndTransactionName : (String, String) = {
      detail.reason.isDefined match {
        case true => ("ctRegistrationSubmissionFailed","CTRegistrationSubmissionFailed")
        case false => ("ctRegistrationSubmissionSuccessful","CTRegistrationSubmission")
      }
    }

    new CTRegistrationAuditEvent(
      detail,
      auditTypeAndTransactionName._1,
      auditTypeAndTransactionName._2
    )
  }

  def ctRegSubmissionFromJson(journeyId : String, json : JsObject) : CTRegistrationSubmissionAuditEventDetails = {
    val des = json.as[DesResponse]
    CTRegistrationSubmissionAuditEventDetails(
      journeyId,
      des.processingDate,
      des.acknowledgementReference,
      des.reason
    )
  }
}
