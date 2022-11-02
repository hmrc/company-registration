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

package connectors.httpParsers

import connectors._
import models.{BusinessRegistration, SubmissionCheckResponse}
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, NOT_FOUND, OK}
import play.api.libs.json.__
import play.api.mvc.Results.Status
import uk.gov.hmrc.http.{HttpException, HttpReads, HttpResponse}

trait IncorporationCheckHttpParsers extends BaseHttpReads { _: BaseConnector =>

  def checkSubmissionHttpParser(timepoint: String): HttpReads[SubmissionCheckResponse] =
    (_: String, _: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          jsonParse[SubmissionCheckResponse](response)("checkSubmissionHttpParser")
        case status =>
          logger.error(s"[checkSubmissionHttpParser] request to SubmissionCheckAPI returned a $status. No incorporations were processed for timepoint $timepoint")
          throw new SubmissionAPIFailure
      }

}

object IncorporationCheckHttpParsers extends IncorporationCheckHttpParsers with BaseConnector
