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

package connectors.httpParsers

import connectors.{BaseConnector, EmailErrorResponse}
import play.api.http.Status.ACCEPTED
import uk.gov.hmrc.http.{HttpReads, HttpResponse}
import utils.AlertLogging

trait SendEmailHttpParsers extends BaseHttpReads with AlertLogging { _: BaseConnector =>

  val sendEmailHttpReads: HttpReads[Boolean] =
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case ACCEPTED =>
          logger.debug(s"[sendEmailHttpReads] request to email service was successful to url: '$url'")
          true
        case status =>
          logger.error(s"[sendEmailHttpReads] request to send email returned a $status - email not sent")
          throw new EmailErrorResponse(status)
      }
}

object SendEmailHttpParsers extends SendEmailHttpParsers with BaseConnector
