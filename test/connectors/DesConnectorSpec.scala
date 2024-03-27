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

import helpers.BaseSpec
import mocks.{MockMetricsService, WSHttpMock}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, SessionId, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class DesConnectorSpec extends BaseSpec with WSHttpMock {

  trait Setup {

    val mockAuditConnector = mock[AuditConnector]
    val mockAuditingConfig = mock[AuditingConfig]

    when(mockAuditConnector.auditingConfig) thenReturn mockAuditingConfig
    when(mockAuditingConfig.auditSource) thenReturn "company-registration"

    val connector = new DesConnector {
      override lazy val serviceURL = "serviceUrl"
      override val http = mockWSHttp
      val urlHeaderEnvironment = "test"
      val urlHeaderAuthorization = "testAuth"
      val auditConnector = mockAuditConnector
      val metricsService = MockMetricsService
      implicit val ec: ExecutionContext = global
    }
  }

  "httpRds" must {

    "return the http response when a 200 status code is read from the http response" in new Setup {
      val response = HttpResponse(200, "")
      connector.httpRds.read("http://", "testUrl", response) mustBe response
    }

    "return a not found exception when it reads a 404 status code from the http response" in new Setup {
      intercept[UpstreamErrorResponse] {
        connector.httpRds.read("http://", "testUrl", HttpResponse(404, ""))
      }
    }
  }

  "DesConnector" must {
    val submission = Json.obj("x" -> "y")
    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "for accepted submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(202, json = Json.obj("x" -> "y"), Map())))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))

      val result = await(connector.ctSubmission("", submission, "testJID"))

      result.status mustBe 202
    }

    "for topup  submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.successful(HttpResponse(202, json = Json.obj("x" -> "y"), Map())))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))

      val result = await(connector.topUpCTSubmission("", submission, "testJID"))

      result.status mustBe 202
    }

    "for a forbidden request, return a bad request" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(UpstreamErrorResponse("", 403, 400)))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))

      intercept[UpstreamErrorResponse] {
        await(connector.ctSubmission("", submission, "testJID"))
      }
    }

    "for a forbidden topup request, return a bad request" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(UpstreamErrorResponse("", 403, 400)))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))

      intercept[UpstreamErrorResponse] {
        await(connector.topUpCTSubmission("", submission, "testJID"))
      }
    }


    "for a client request timedout, return unavailable" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).
        thenReturn(Future.failed(UpstreamErrorResponse("", 499, 502)))

      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Success))

      intercept[UpstreamErrorResponse] {
        await(connector.ctSubmission("", submission, "testJID"))
      }
    }
  }

  "customDESRead" must {

    "return the response on an acceptable request" in new Setup {
      val response = HttpResponse(202, "")
      connector.customDESRead("", "", response) mustBe response
    }

    "return a UpstreamErrorResponse on a bad request" in new Setup {
      intercept[UpstreamErrorResponse] {
        connector.customDESRead("", "", HttpResponse(400, ""))
      }
    }

    "return the HttpResponse as a 202 on a conflict" in new Setup {
      connector.customDESRead("", "", HttpResponse(409, "")).status mustBe 202
    }

    "return a UpstreamErrorResponse on a timeout" in new Setup {
      val ex = intercept[UpstreamErrorResponse] {
        connector.customDESRead("", "", HttpResponse(499, ""))
      }
      ex.reportAs mustBe 502
    }
    "return a UpstreamErrorResponse when response is 503" in new Setup {
      val ex = intercept[UpstreamErrorResponse] {
        connector.customDESRead("", "", HttpResponse(429, ""))
      }
      ex.reportAs mustBe 503
    }
  }
}
