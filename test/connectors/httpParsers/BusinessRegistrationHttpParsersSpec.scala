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
import connectors.{BusinessRegistrationForbiddenResponse, BusinessRegistrationNotFoundResponse, BusinessRegistrationSuccessResponse}
import fixtures.BusinessRegistrationFixture
import helpers.BaseSpec
import play.api.http.Status.{CREATED, FORBIDDEN, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.http.HttpResponse
import utils.LogCapturingHelper

class BusinessRegistrationHttpParsersSpec extends BaseSpec with BusinessRegistrationFixture with LogCapturingHelper {

  val regId = "reg1234"

  "BusinessRegistrationHttpParsers" when {

    "calling .createBusinessRegistrationHttpParser" when {

      val rds = BusinessRegistrationHttpParsers.createBusinessRegistrationHttpParser

      "response is CREATED and JSON is valid" must {

        "return the Business Profile" in {

          val response = HttpResponse(CREATED, json = Json.toJson(validBusinessRegistrationResponse), Map())
          rds.read("", "", response) mustBe validBusinessRegistrationResponse
        }
      }

      "response is CREATED and JSON is malformed" must {

        "return a JsResultException and log an error message" in {

          val response = HttpResponse(CREATED, json = Json.obj(), Map())

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](rds.read("", "", response))
            logs.containsMsg(Level.ERROR, "[BusinessRegistrationHttpParsers][createBusinessRegistrationHttpParser] JSON returned could not be parsed to models.BusinessRegistration model")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Exception and log an error" in {

          val response = HttpResponse(INTERNAL_SERVER_ERROR, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[Exception](rds.read("", "/foo/bar", response))
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][createBusinessRegistrationHttpParser] Calling url: '/foo/bar' returned unexpected status: '$INTERNAL_SERVER_ERROR'")
          }
        }
      }
    }

    "calling .retrieveBusinessRegistrationHttpParser(regId: Option[String])" when {

      val rds = BusinessRegistrationHttpParsers.retrieveBusinessRegistrationHttpParser(Some(regId))

      "response is OK and JSON is valid" must {

        "return the Business Profile" in {

          val response = HttpResponse(OK, json = Json.toJson(validBusinessRegistrationResponse), Map())
          rds.read("", "", response) mustBe BusinessRegistrationSuccessResponse(validBusinessRegistrationResponse)
        }
      }

      "response is OK and JSON is malformed" must {

        "return a JsResultException and log an error message" in {

          val response = HttpResponse(OK, json = Json.obj(), Map())

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](rds.read("", "", response))
            logs.containsMsg(Level.ERROR, "[BusinessRegistrationHttpParsers][retrieveBusinessRegistrationHttpParser] JSON returned could not be parsed to models.BusinessRegistration model")
          }
        }
      }

      "response is NOT_FOUND" must {

        "return a BusinessRegistrationNotFoundResponse and log an info message" in {

          val response = HttpResponse(NOT_FOUND, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            rds.read("", "", response) mustBe BusinessRegistrationNotFoundResponse
            logs.containsMsg(Level.INFO, s"[BusinessRegistrationHttpParsers][retrieveBusinessRegistrationHttpParser] Received a NotFound status code when expecting metadata from Business-Registration for regId: '$regId'")
          }
        }
      }

      "response is FORBIDDEN" must {

        "return a BusinessRegistrationForbiddenResponse and log an error message" in {

          val response = HttpResponse(FORBIDDEN, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            rds.read("", "", response) mustBe BusinessRegistrationForbiddenResponse
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][retrieveBusinessRegistrationHttpParser] Received a Forbidden status code when expecting metadata from Business-Registration for regId: '$regId'")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Exception and log an error" in {

          val response = HttpResponse(INTERNAL_SERVER_ERROR, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[Exception](rds.read("", "/foo/bar", response))
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][retrieveBusinessRegistrationHttpParser] Calling url: '/foo/bar' returned unexpected status: '$INTERNAL_SERVER_ERROR'")
          }
        }
      }
    }

    "calling .removeMetadataHttpReads(regId: String)" when {

      val rds = BusinessRegistrationHttpParsers.removeMetadataHttpReads(regId)

      "response is OK" must {

        "return true" in {

          rds.read("", "", HttpResponse(OK, "")) mustBe true
        }
      }

      "response is NOT_FOUND" must {

        "return false and log an info message" in {

          val response = HttpResponse(NOT_FOUND, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            rds.read("", "", response) mustBe false
            logs.containsMsg(Level.INFO, s"[BusinessRegistrationHttpParsers][removeMetadataHttpReads] Received a NotFound status code when attempting to remove a metadata document for regId: '$regId'")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return false and log an error" in {

          val response = HttpResponse(INTERNAL_SERVER_ERROR, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            rds.read("", "/foo/bar", response) mustBe false
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][removeMetadataHttpReads] Calling url: '/foo/bar' returned unexpected status: '$INTERNAL_SERVER_ERROR'")
          }
        }
      }
    }

    "calling .removeMetadataAdminHttpReads(regId: String)" when {

      val rds = BusinessRegistrationHttpParsers.removeMetadataAdminHttpReads(regId)

      "response is OK" must {

        "return true" in {

          rds.read("", "", HttpResponse(OK, "")) mustBe true
        }
      }

      "response is NOT_FOUND" must {

        "return false and log an info message" in {

          val response = HttpResponse(NOT_FOUND, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            rds.read("", "", response) mustBe false
            logs.containsMsg(Level.INFO, s"[BusinessRegistrationHttpParsers][removeMetadataAdminHttpReads] Received a NotFound status code when attempting to remove a metadata document for regId: '$regId'")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "throw exception and log an error" in {

          val response = HttpResponse(INTERNAL_SERVER_ERROR, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[Exception](rds.read("", "/foo/bar", response))
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][removeMetadataAdminHttpReads] Calling url: '/foo/bar' returned unexpected status: '$INTERNAL_SERVER_ERROR' for regId: '$regId'")
          }
        }
      }
    }

    "calling .dropMetadataCollectionHttpReads" when {

      val rds = BusinessRegistrationHttpParsers.dropMetadataCollectionHttpReads

      "response is OK and JSON is valid" must {

        "return the Business Profile" in {

          val response = HttpResponse(OK, json = Json.obj("message" -> "success!"), Map())
          rds.read("", "", response) mustBe "success!"
        }
      }

      "response is OK and JSON is malformed" must {

        "return a JsResultException and log an error message" in {

          val response = HttpResponse(OK, json = Json.obj(), Map())

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[JsResultException](rds.read("", "", response))
            logs.containsMsg(Level.ERROR, "[BusinessRegistrationHttpParsers][dropMetadataCollectionHttpReads] JSON returned could not be parsed to java.lang.String model")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an Exception and log an error" in {

          val response = HttpResponse(INTERNAL_SERVER_ERROR, "")

          withCaptureOfLoggingFrom(BusinessRegistrationHttpParsers.logger) { logs =>
            intercept[Exception](rds.read("", "/foo/bar", response))
            logs.containsMsg(Level.ERROR, s"[BusinessRegistrationHttpParsers][dropMetadataCollectionHttpReads] Calling url: '/foo/bar' returned unexpected status: '$INTERNAL_SERVER_ERROR'")
          }
        }
      }
    }
  }
}
