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

import connectors.httpParsers.SendEmailHttpParsers
import models.SendEmailRequest
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class EmailErrorResponse(s: Int) extends NoStackTrace

@Singleton
class SendEmailConnectorImpl @Inject()(servicesConfig: ServicesConfig,
                                       val http: HttpClient
                                      )(implicit val ec: ExecutionContext) extends SendEmailConnector with HttpErrorFunctions {
  val sendEmailURL: String = servicesConfig.getConfString("email.sendEmailURL", throw new Exception("email.sendEmailURL not found"))
}

trait SendEmailConnector extends BaseConnector with SendEmailHttpParsers {
  implicit val ec: ExecutionContext
  val http: HttpClient
  val sendEmailURL: String

  def requestEmail(EmailRequest: SendEmailRequest)(implicit hc: HeaderCarrier): Future[Boolean] = {
    withRecovery()("requestEmail") {
      http.POST[SendEmailRequest, Boolean](s"$sendEmailURL", EmailRequest)(SendEmailRequest.format, sendEmailHttpReads, hc, ec)
    }
  }
}