/*
 * Copyright 2024 HM Revenue & Customs
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

import connectors.httpParsers.IncorporationCheckHttpParsers
import models.SubmissionCheckResponse
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class SubmissionAPIFailure extends NoStackTrace

@Singleton
class IncorporationCheckAPIConnectorImpl @Inject()(servicesConfig: ServicesConfig,
                                                   val http: HttpClient
                                                  )(implicit val ec: ExecutionContext) extends IncorporationCheckAPIConnector {
  lazy val businessRegUrl: String = servicesConfig.baseUrl("business-registration")
  override lazy val proxyUrl: String = servicesConfig.baseUrl("company-registration-frontend")

}

trait IncorporationCheckAPIConnector extends BaseConnector with IncorporationCheckHttpParsers {

  implicit val ec: ExecutionContext
  val proxyUrl: String
  val http: HttpClient

  def checkSubmission(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[SubmissionCheckResponse] = {
    val tp = timepoint.fold("")(t => s"timepoint=$t&")
    withRecovery(throw new SubmissionAPIFailure)("checkSubmission") {
      http.GET[SubmissionCheckResponse](s"$proxyUrl/internal/check-submission?${tp}items_per_page=1")(checkSubmissionHttpParser(tp), hc, ec)
    }
  }
}