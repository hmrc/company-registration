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

import ch.qos.logback.classic.Level
import connectors.EmailErrorResponse
import helpers.BaseSpec
import play.api.http.Status._
import uk.gov.hmrc.http.HttpResponse
import utils.LogCapturingHelper

class SendEmailHttpParsersSpec extends BaseSpec with LogCapturingHelper {

  "SendEmailHttpParsers" when {

    "calling .sendEmailHttpReads" when {

      val rds = SendEmailHttpParsers.sendEmailHttpReads

      "response is ACCEPTED (202)" must {

        "return true and log a debug msg" in {

          withCaptureOfLoggingFrom(SendEmailHttpParsers.logger) { logs =>
            rds.read("", "/foo/bar/send/mail", HttpResponse(ACCEPTED, "")) mustBe true

            logs.containsMsg(Level.DEBUG, s"[SendEmailHttpParsers][sendEmailHttpReads] request to email service was successful to url: '/foo/bar/send/mail'")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an EmailErrorResponse and log an error" in {

          val response = HttpResponse(INTERNAL_SERVER_ERROR, "")

          withCaptureOfLoggingFrom(SendEmailHttpParsers.logger) { logs =>
            intercept[EmailErrorResponse](rds.read("", "/foo/bar", response))
            logs.containsMsg(Level.ERROR, s"[SendEmailHttpParsers][sendEmailHttpReads] request to send email returned a $INTERNAL_SERVER_ERROR - email not sent")
          }
        }
      }
    }
  }
}
