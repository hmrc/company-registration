/*
 * Copyright 2023 HM Revenue & Customs
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

package connectors.httpParsers

import connectors._
import models.SubmissionCheckResponse
import play.api.http.Status.{ACCEPTED, NOT_FOUND, NO_CONTENT, OK}
import uk.gov.hmrc.http.{HttpReads, HttpResponse, NotFoundException}
import utils.{AlertLogging, PagerDutyKeys}

trait IncorporationInformationHttpParsers extends BaseHttpReads with AlertLogging { _: BaseConnector =>

  def registerInterestHttpParser(regId: String, txId: String): HttpReads[Boolean] =
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case ACCEPTED =>
          logger.info(s"[registerInterestHttpParser] Registration forced returned 202" + logContext(Some(regId), Some(txId)))
          true
        case status =>
          unexpectedStatusHandling()("registerInterestHttpParser", url, status)
      }

  def cancelSubscriptionHttpParser(regId: String, txId: String): HttpReads[Boolean] =
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          logger.info(s"[cancelSubscriptionHttpParser] Cancelled subscription" + logContext(Some(regId), Some(txId)))
          true
        case NOT_FOUND =>
          val msg = s"[cancelSubscriptionHttpParser] No subscription to cancel" + logContext(Some(regId), Some(txId))
          logger.info(msg)
          throw new NotFoundException(msg)
        case status =>
          unexpectedStatusHandling()("cancelSubscriptionHttpParser", url, status)
      }

  val checkCompanyIncorporatedHttpParser: HttpReads[Option[String]] =
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          val crn = (response.json \ "crn").asOpt[String]
          if (crn.nonEmpty) {
            pagerduty(PagerDutyKeys.STALE_DOCUMENTS_DELETE_WARNING_CRN_FOUND)
          }
          crn
        case NO_CONTENT =>
          None
        case status =>
          unexpectedStatusHandling()("checkCompanyIncorporatedHttpParser", url, status)
      }
}

object IncorporationInformationHttpParsers extends IncorporationInformationHttpParsers with BaseConnector
