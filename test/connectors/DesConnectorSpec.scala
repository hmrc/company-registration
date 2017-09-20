/*
 * Copyright 2017 HM Revenue & Customs
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

import java.util.UUID

import mocks.MockMetricsService
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorSpec extends UnitSpec with MockitoSugar {

  object TestAuditConnector extends AuditConnector with AppName with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig("auditing")
  }

  class MockHttp extends WSGet with WSPost with WSPut with HttpAuditing {
    override val hooks = Seq(AuditingHook)
    override def auditConnector: AuditConnector = TestAuditConnector
    override def appName = "company-registration"
  }

  val mockWSHttp = mock[MockHttp]
  val mockAuditConnector = mock[AuditConnector]

  trait Setup {
    val connector = new DesConnector {
      override lazy val serviceURL = "serviceUrl"
      override val http = mockWSHttp
      val urlHeaderEnvironment = "test"
      val urlHeaderAuthorization = "testAuth"
      val auditConnector = mockAuditConnector
      val metricsService = MockMetricsService
    }
  }

  "httpRds" should {

    "return the http response when a 200 status code is read from the http response" in new Setup {
      val response = HttpResponse(200)
      connector.httpRds.read("http://", "testUrl", response) shouldBe response
    }

    "return a not found exception when it reads a 404 status code from the http response" in new Setup {
      intercept[Upstream4xxResponse]{
        connector.httpRds.read("http://", "testUrl", HttpResponse(404))
      }
    }
  }

  "DesConnector" should {
    val submission = Json.obj("x" -> "y")
    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "for accepted submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(202, responseJson = Some(Json.obj("x"->"y")))))

      when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      val result = await(connector.ctSubmission("",submission, "testJID"))

      result.status shouldBe 202
    }

    "for topup  submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(202, responseJson = Some(Json.obj("x"->"y")))))

      when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      val result = await(connector.topUpCTSubmission("",submission, "testJID"))

      result.status shouldBe 202
    }

    "for a forbidden request, return a bad request" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.failed(Upstream4xxResponse("", 403, 400)))

      when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      intercept[Upstream4xxResponse] {
        await(connector.ctSubmission("", submission, "testJID"))
      }
    }

    "for a forbidden topup request, return a bad request" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.failed(Upstream4xxResponse("", 403, 400)))

      when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      intercept[Upstream4xxResponse] {
        await(connector.topUpCTSubmission("", submission, "testJID"))
      }
    }


    "for a client request timedout, return unavailable" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.failed(Upstream4xxResponse("", 499, 502)))

      when(mockAuditConnector.sendEvent(Matchers.any())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(Success))

      intercept[Upstream4xxResponse] {
        await(connector.ctSubmission("", submission, "testJID"))
      }
    }
  }

  "customDESRead" should {

    "return the response on an acceptable request" in new Setup {
      val response = HttpResponse(202)
      await(connector.customDESRead("", "", response)) shouldBe response
    }

    "return a Upstream4xxResponse on a bad request" in new Setup {
      val response = HttpResponse(400)
      intercept[Upstream4xxResponse] {
        await(connector.customDESRead("", "", response))
      }
    }

    "return the HttpResponse as a 202 on a conflict" in new Setup {
      val response = HttpResponse(409)
      await(connector.customDESRead("", "", response)).status shouldBe 202
    }

    "return a Upstream4xxResponse on a timeout" in new Setup {
      val response = HttpResponse(499)
      val ex = intercept[Upstream4xxResponse] {
        await(connector.customDESRead("", "", response))
      }
      ex.reportAs shouldBe 502
    }
  }
}
