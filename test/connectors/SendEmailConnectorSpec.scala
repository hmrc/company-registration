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

package connectors

import helpers.BaseSpec
import models.SendEmailRequest
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import uk.gov.hmrc.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SendEmailConnectorSpec extends BaseSpec {

  trait Setup {
    val connector = new SendEmailConnector {
      override val sendEmailURL = "testSendEmailURL"
      override val http = mockWSHttp
      implicit val ec: ExecutionContext = global
    }
  }

  implicit val hc = HeaderCarrier()

  val verifiedEmail = Array("verified@email.com")
  val returnLinkURL = "testReturnLinkUrl"
  val emailRequest = SendEmailRequest(
    to = verifiedEmail,
    templateId = "register_your_company_register_vat_email",
    parameters = Map(),
    force = true
  )

  "send Email" should {

    "Return a true when a request to send a new email is successful" in new Setup {
      mockHttpPOST(connector.sendEmailURL, HttpResponse(ACCEPTED))

      await(connector.requestEmail(emailRequest)) shouldBe true
    }

    "Fail the future when the service cannot be found" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when we send a bad request" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when EVS returns an internal server error" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new InternalServerException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when EVS returns an upstream error" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadGatewayException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

  }

  "customRead" should {
    "return a 200" in new Setup {
      val expected = HttpResponse(OK)
      val result = connector.customRead("test", "test", expected)
      result.status shouldBe expected.status
    }
    "return a 409" in new Setup {
      val expected = HttpResponse(CONFLICT)
      val result = connector.customRead("test", "test", HttpResponse(CONFLICT))
      result.status shouldBe expected.status
    }
    "return a BadRequestException" in new Setup {
      val response = HttpResponse(BAD_REQUEST)
      intercept[BadRequestException](connector.customRead("test", "test", response))
    }
    "return a NotFoundException" in new Setup {
      val response = HttpResponse(NOT_FOUND)
      intercept[NotFoundException](connector.customRead("test", "test", response))
    }
    "return an InternalServerException" in new Setup {
      val response = HttpResponse(INTERNAL_SERVER_ERROR)
      intercept[InternalServerException](connector.customRead("test", "test", response))
    }
    "return a BadGatewayException" in new Setup {
      val response = HttpResponse(BAD_GATEWAY)
      intercept[BadGatewayException](connector.customRead("test", "test", response))
    }
    "return an upstream 4xx" in new Setup {
      val response = HttpResponse(UNAUTHORIZED)
      intercept[Upstream4xxResponse](connector.customRead("test", "test", response))
    }
  }
}