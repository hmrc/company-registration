/*
 * Copyright 2016 HM Revenue & Customs
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

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.Play
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class DesConnectorSpec extends UnitSpec with OneServerPerSuite with MockitoSugar with BeforeAndAfter {

  object TestAuditConnector extends AuditConnector with AppName with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig("auditing")
  }

  class MockHttp extends WSGet with WSPost with WSPut with HttpAuditing {
    override val hooks = Seq(AuditingHook)
    override def auditConnector: AuditConnector = TestAuditConnector
    override def appName = Play.configuration.getString("appName").getOrElse("company-registration")
  }

  val mockWSHttp = mock[MockHttp]

  trait Setup {
    val connector = new DesConnector {
      override lazy val serviceURL = "serviceUrl"
      override val http = mockWSHttp
      val urlHeaderEnvironment = "test"
      val urlHeaderAuthorization = "testAuth"
    }
  }

  before {
    reset(mockWSHttp)
  }

  "DesConnector" should {
    val submission = Json.obj("x" -> "y")
    implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))

    "for a successful submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(200, responseJson = Some(Json.obj()))))

      val result = await(connector.ctSubmission("",submission))

      result shouldBe SuccessDesResponse
    }

    "for accepted submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(202, responseJson = Some(Json.obj()))))

      val result = await(connector.ctSubmission("",submission))

      result shouldBe SuccessDesResponse
    }

    "for a conflicted submission, return success" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(409, responseJson = Some(Json.obj()))))

      val result = await(connector.ctSubmission("",submission))

      result shouldBe SuccessDesResponse
    }

    "for an invalid request, return the reason" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(400, responseJson = Some(Json.obj("reason" -> "wibble")))))

      val result = await(connector.ctSubmission("",submission))

      result shouldBe InvalidDesRequest("wibble")
    }


  }
}
