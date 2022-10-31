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

import fixtures.BusinessRegistrationFixture
import helpers.BaseSpec
import models.IncorpStatus
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import java.time.{Instant, LocalDate}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class IncorporationInformationConnectorSpec extends BaseSpec with BusinessRegistrationFixture with LogCapturing {

  val tRegime = "testRegime"
  val tSubscriber = "testSubscriber"

  trait Setup {
    object Connector extends IncorporationInformationConnector {
      override val iiUrl = "testIncorporationInformationUrl"
      override val http = mockWSHttp
      override val regime = tRegime
      override val subscriber = tSubscriber
      override val companyRegUrl: String = "testCompanyRegistrationUrl"
      implicit val ec: ExecutionContext = global
    }
  }

  implicit val hc = HeaderCarrier()
  implicit val req: Request[AnyContent] = FakeRequest()

  val regId = "reg-id-12345"
  val txId = "tx-id-12345"
  val crn = "1234567890"
  val status = "accepted"
  val description = "Some description"
  val incorpDate = "2017-04-25"

  val incorpInfoResponse = Json.parse(
    s"""
       |{
       |  "SCRSIncorpStatus":{
       |    "IncorpSubscriptionKey":{
       |      "subscriber":"SCRS",
       |      "discriminator":"CT",
       |      "transactionId":"$txId"
       |    },
       |    "SCRSIncorpSubscription":{
       |      "callbackUrl":"/callBackUrl"
       |    },
       |    "IncorpStatusEvent":{
       |      "status":"$status",
       |      "crn":"$crn",
       |      "incorporationDate":"$incorpDate",
       |      "description":"$description",
       |      "timestamp":"${Instant.parse("2017-04-25T00:00:00.000Z").toEpochMilli}"
       |    }
       |  }
       |}
      """.stripMargin)


  val expected = IncorpStatus(txId, status, Some(crn), Some(description), Some(LocalDate.parse(incorpDate)))

  "callBackUrl" must {
    "return admin url when admin is true" in new Setup {
      Connector.callBackurl(true) mustBe "testCompanyRegistrationUrl/corporation-tax-registration/process-admin-incorp"
    }
    "return non admin url when admin is false" in new Setup {
      Connector.callBackurl(false) mustBe "testCompanyRegistrationUrl/corporation-tax-registration/process-incorp"

    }
  }

  "createMetadataEntry" must {
    "make a http POST request to Incorporation Information micro-service to register an interest and return 202" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(202, "")))

      await(Connector.registerInterest(regId, txId)) mustBe true
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any other 2xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, "")))

      intercept[RuntimeException](await(Connector.registerInterest(regId, txId)))
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any 4xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(400, "")))

      intercept[RuntimeException](await(Connector.registerInterest(regId, txId)))
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any 5xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(500, "")))

      intercept[RuntimeException](await(Connector.registerInterest(regId, txId)))
    }
  }

  "cancelSubscription" must {
    "make a http DELETE request to Incorporation Information micro-service to register an interest and return 200" in new Setup {
      val expectedURL = s"${Connector.iiUrl}/incorporation-information/subscribe/$txId/regime/$tRegime/subscriber/$tSubscriber?force=true"
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, "")))

      await(Connector.cancelSubscription(regId, txId)) mustBe true
    }
    "make a http DELETE request to Incorporation Information micro-service to register an interest and return a 404" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[NotFoundException](await(Connector.cancelSubscription(regId, txId)))
    }
    "not make a http DELETE request to Incorporation Information micro-service to register an interest and return any other 2xx" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(202, "")))

      intercept[RuntimeException](await(Connector.cancelSubscription(regId, txId)))
    }
    "not make a http DELETE request to Incorporation Information micro-service to register an interest and return any 5xx" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("500", 502, 500)))

      intercept[RuntimeException](await(Connector.cancelSubscription(regId, txId)))
    }

    "use the old regime" in new Setup {
      val oldRegime = "ct"
      val expectedURL = s"${Connector.iiUrl}/incorporation-information/subscribe/$txId/regime/$oldRegime/subscriber/$tSubscriber?force=true"
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, "")))

      await(Connector.cancelSubscription(regId, txId, useOldRegime = true)) mustBe true
    }
  }

  "checkNotIncorporated" must {
    "return None" when {
      "the CRN is not there" in new Setup {
        val expectedURL = s"${Connector.iiUrl}/incorporation-information/$txId/incorporation-update"
        when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, json = Json.obj(), Map())))

        await(Connector.checkCompanyIncorporated(txId)) mustBe None
      }

      "on a no content response" in new Setup {
        val expectedURL = s"${Connector.iiUrl}/incorporation-information/$txId/incorporation-update"
        when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(204, "")))

        await(Connector.checkCompanyIncorporated(txId)) mustBe None
      }
    }

    "return Some crn" when {
      "the CRN is there" in new Setup {
        val expectedURL = s"${Connector.iiUrl}/incorporation-information/$txId/incorporation-update"
        when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, json = Json.obj("crn" -> "crn"), Map())))

        withCaptureOfLoggingFrom(Connector.logger) { logs =>
          await(Connector.checkCompanyIncorporated(txId)) mustBe Some("crn")

          logs.size mustBe 1
          logs.head.getMessage mustBe "[Connector] STALE_DOCUMENTS_DELETE_WARNING_CRN_FOUND"
        }
      }
    }
  }
}
