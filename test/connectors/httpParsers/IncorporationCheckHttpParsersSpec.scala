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
import connectors.SubmissionAPIFailure
import helpers.BaseSpec
import models.SubmissionCheckResponse
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.{JsObject, JsResultException, Json}
import uk.gov.hmrc.http.HttpResponse
import utils.LogCapturingHelper

class IncorporationCheckHttpParsersSpec extends BaseSpec with LogCapturingHelper {

  val submissionCheckResponse: SubmissionCheckResponse = SubmissionCheckResponse(Seq(), "/foo/bar")
  val submissionCheckResponseJson: JsObject = Json.obj(
    "items" -> Json.arr(),
    "links" -> Json.obj(
      "next" -> "/foo/bar"
    )
  )

  "IncorporationCheckHttpParsers" when {

    "calling .checkSubmissionHttpParser" when {

      val rds = IncorporationCheckHttpParsers.checkSubmissionHttpParser("timepoint=now")

      "response is 2xx and JSON is valid" must {

        "return the SubmissionCheckResponse" in {

          val response = HttpResponse(OK, json = submissionCheckResponseJson, Map())
          rds.read("", "", response) mustBe submissionCheckResponse
        }
      }

      "response is OK and JSON is malformed" must {

        "return a JsResultException and log an error message" in {

          val response = HttpResponse(OK, json = Json.obj(), Map())

          withCaptureOfLoggingFrom(IncorporationCheckHttpParsers.logger) { logs =>
            intercept[JsResultException](rds.read("", "", response))
            logs.containsMsg(Level.ERROR, "[IncorporationCheckHttpParsers][checkSubmissionHttpParser] JSON returned could not be parsed to models.SubmissionCheckResponse model")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Exception and log an error" in {

          val response = HttpResponse(INTERNAL_SERVER_ERROR, "")

          withCaptureOfLoggingFrom(IncorporationCheckHttpParsers.logger) { logs =>
            intercept[SubmissionAPIFailure](rds.read("", "/foo/bar", response))
            logs.containsMsg(Level.ERROR, s"[IncorporationCheckHttpParsers][checkSubmissionHttpParser] request to SubmissionCheckAPI returned a $INTERNAL_SERVER_ERROR. No incorporations were processed for timepoint timepoint=now")
          }
        }
      }
    }
  }
}
