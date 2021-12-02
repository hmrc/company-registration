/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors

import models.SubmissionCheckResponse
import play.api.Logging
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class SubmissionAPIFailure extends NoStackTrace

@Singleton
class IncorporationCheckAPIConnectorImpl @Inject()(servicesConfig: ServicesConfig,
                                                   val http: HttpClient
                                                  )(implicit val ec: ExecutionContext) extends IncorporationCheckAPIConnector {
  lazy val businessRegUrl = servicesConfig.baseUrl("business-registration")
  override lazy val proxyUrl = servicesConfig.baseUrl("company-registration-frontend")

}

trait IncorporationCheckAPIConnector extends Logging {

  implicit val ec: ExecutionContext
  val proxyUrl: String
  val http: HttpClient

  def logError(ex: HttpException, timepoint: Option[String]) = {
    logger.error(s"[IncorporationCheckAPIConnector] [checkSubmission]" +
      s" request to SubmissionCheckAPI returned a ${ex.responseCode}. " +
      s"No incorporations were processed for timepoint ${timepoint} - Reason = ${ex.getMessage}")
  }

  def checkSubmission(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[SubmissionCheckResponse] = {
    val tp = timepoint.fold("")(t => s"timepoint=$t&")
    http.GET[SubmissionCheckResponse](s"$proxyUrl/internal/check-submission?${tp}items_per_page=1") map {
      res => res
    } recover {
      case ex: BadRequestException =>
        logError(ex, timepoint)
        throw new SubmissionAPIFailure
      case ex: NotFoundException =>
        logError(ex, timepoint)
        throw new SubmissionAPIFailure
      case ex: Upstream4xxResponse =>
        logger.error("[IncorporationCheckAPIConnector] [checkSubmission]" + ex.upstreamResponseCode + " " + ex.message)
        throw new SubmissionAPIFailure
      case ex: Upstream5xxResponse =>
        logger.error("[IncorporationCheckAPIConnector] [checkSubmission]" + ex.upstreamResponseCode + " " + ex.message)
        throw new SubmissionAPIFailure
      case ex: Exception =>
        logger.error("[IncorporationCheckAPIConnector] [checkSubmission]" + ex)
        throw new SubmissionAPIFailure
    }
  }
}