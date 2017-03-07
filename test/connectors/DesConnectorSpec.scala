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

import helpers.SCRSSpec
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.Play
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DesConnectorSpec extends SCRSSpec with BeforeAndAfter {

  object TestAuditConnector extends AuditConnector with AppName with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig("auditing")
  }

  class MockHttp extends WSGet with WSPost with WSPut with HttpAuditing {
    override val hooks = Seq(AuditingHook)
    override def auditConnector: AuditConnector = TestAuditConnector
    override def appName = fakeApplication.configuration.getString("appName").getOrElse("company-registration")
  }

  val mockAuditConnector = mock[AuditConnector]

  trait Setup {
    val connector = new DesConnector {
      override lazy val serviceURL = "serviceUrl"
      override val http = mockWSHttp
      val urlHeaderEnvironment = "test"
      val urlHeaderAuthorization = "testAuth"
      val auditConnector = mockAuditConnector
    }
  }

  before {
    reset(mockWSHttp)
  }

  "DesConnector" should {

    "use the correct serviceURL" in {
      DesConnector.serviceURL shouldBe "http://localhost:9642"
    }
  }

  "httpRds" should {

    "return the http response when a 200 status code is read from the http response" in {
      val response = HttpResponse(200)
      DesConnector.httpRds.read("http://", "testUrl", response) shouldBe response
    }

    "return a not found exception when it reads a 404 status code from the http response" in {
      intercept[NotFoundException]{
        DesConnector.httpRds.read("http://", "testUrl", HttpResponse(404))
      }
    }
  }

  "DesConnector" should {
    val submission = Json.obj("x" -> "y")
    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "for a successful submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(200, responseJson = Some(Json.obj("x"->"y")))))

      val result = await(connector.ctSubmission("",submission, "testJID")(hc))

      result shouldBe SuccessDesResponse(Json.obj("x"->"y"))
    }

    "for accepted submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(202, responseJson = Some(Json.obj("x"->"y")))))

      val result = await(connector.ctSubmission("",submission, "testJID")(hc))

      result shouldBe SuccessDesResponse(Json.obj("x"->"y"))
    }

    "for a conflicted submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(409, responseJson = Some(Json.obj("x"->"y")))))

      val result = await(connector.ctSubmission("",submission,"testJID")(hc))

      result shouldBe SuccessDesResponse(Json.obj("x"->"y"))
    }

    "for an invalid request, return the reason" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(400, responseJson = Some(Json.obj("reason" -> "wibble")))))

      val result = await(connector.ctSubmission("",submission,"testJID")(hc))

      result shouldBe InvalidDesRequest("wibble")
    }

    "return a NotFoundDesResponse" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.failed(new NotFoundException("")))

      val result = await(connector.ctSubmission("",submission,"testJID")(hc))

      result shouldBe NotFoundDesResponse
    }

    "return a DesErrorResponse when an InternalServerException occurs" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.failed(new InternalServerException("")))

      val result = await(connector.ctSubmission("",submission,"testJID")(hc))

      result shouldBe DesErrorResponse
    }

    "return a DesErrorResponse when a BadGatewayException occurs" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.failed(new BadGatewayException("")))

      val result = await(connector.ctSubmission("",submission,"testJID")(hc))

      result shouldBe DesErrorResponse
    }

    "return a DesErrorResponse when an uncaught exception occurs" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.failed(new Exception("")))

      val result = await(connector.ctSubmission("",submission,"testJID")(hc))

      result shouldBe DesErrorResponse
    }
  }

  "customDESRead" should {

    "return the HttpResponse on a bad request" in new Setup {
      val response = HttpResponse(400)
      await(connector.customDESRead("", "", response)) shouldBe response
    }

    "throw a NotFoundException" in new Setup {
      val response = HttpResponse(404)
      val ex = intercept[NotFoundException]{
        await(connector.customDESRead("", "", response))
      }
      ex.getMessage shouldBe "ETMP returned a Not Found status"
    }

    "return the HttpResponse on a conflict" in new Setup {
      val response = HttpResponse(409)
      await(connector.customDESRead("", "", response)) shouldBe response
    }

    "throw an InternalServerException" in new Setup {
      val response = HttpResponse(500)
      val ex = intercept[InternalServerException]{
        await(connector.customDESRead("", "", response))
      }
      ex.getMessage shouldBe "ETMP returned an internal server error"
    }

    "throw an BadGatewayException" in new Setup {
      val response = HttpResponse(502)
      val ex = intercept[BadGatewayException]{
        await(connector.customDESRead("", "", response))
      }
      ex.getMessage shouldBe "ETMP returned an upstream error"
    }

    "return an Upstream4xxResponse when an uncaught 4xx Http response status is found" in new Setup {
      val response = HttpResponse(405)
      val ex = intercept[Upstream4xxResponse]{
        await(connector.customDESRead("http://", "testUrl", response))
      }
      ex.getMessage shouldBe "http:// of 'testUrl' returned 405. Response body: 'null'"
    }

    "return an Upstream5xxResponse when an uncaught 5xx Http response status is found" in new Setup {
      val response = HttpResponse(505)
      val ex = intercept[Upstream5xxResponse]{
        await(connector.customDESRead("http://", "testUrl", response))
      }
      ex.getMessage shouldBe "http:// of 'testUrl' returned 505. Response body: 'null'"
    }
  }
}
