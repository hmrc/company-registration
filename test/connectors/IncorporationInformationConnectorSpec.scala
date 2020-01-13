/*
 * Copyright 2019 HM Revenue & Customs
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
import mocks.SCRSMocks
import models.IncorpStatus
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}

import scala.concurrent.Future

class IncorporationInformationConnectorSpec extends UnitSpec with MockitoSugar with SCRSMocks with BusinessRegistrationFixture with LogCapturing {

  val tRegime = "testRegime"
  val tSubscriber = "testSubscriber"

  trait Setup {
    val connector = new IncorporationInformationConnector {
      override val iiUrl = "testUrl"
      override val http = mockWSHttp
      override val regime = tRegime
      override val subscriber = tSubscriber
      override val companyRegUrl: String = "http://foo/bar"
    }
  }

  implicit val hc = HeaderCarrier()
  implicit val req: Request[AnyContent] = FakeRequest()

  val regId = "reg-id-12345"
  val txId  = "tx-id-12345"
  val crn   = "1234567890"
  val status      = "accepted"
  val description = "Some description"
  val incorpDate  = "2017-04-25"

  val incorpInfoResponse = Json.parse(s"""
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
      |      "timestamp":"${DateTime.parse("2017-04-25").getMillis}"
      |    }
      |  }
      |}
      """.stripMargin)



  val expected = IncorpStatus(txId, status, Some(crn), Some(description), Some(DateTime.parse(incorpDate)))

  "callBackUrl" should {
    "return admin url when admin is true" in new Setup {
      connector.callBackurl(true) shouldBe "http://foo/bar/corporation-tax-registration/process-admin-incorp"
    }
    "return non admin url when admin is false" in new Setup {
      connector.callBackurl(false) shouldBe "http://foo/bar/corporation-tax-registration/process-incorp"

    }
  }

  "createMetadataEntry" should {
    "make a http POST request to Incorporation Information micro-service to register an interest and return 202" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(HttpResponse(202))

      await(connector.registerInterest(regId, txId)) shouldBe true
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any other 2xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(HttpResponse(200))

      intercept[RuntimeException](await(connector.registerInterest(regId, txId)))
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any 4xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(HttpResponse(400))

      intercept[RuntimeException](await(connector.registerInterest(regId, txId)))
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any 5xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(HttpResponse(500))

      intercept[RuntimeException](await(connector.registerInterest(regId, txId)))
    }
  }

  "cancelSubscription" should {
    "make a http DELETE request to Incorporation Information micro-service to register an interest and return 202" in new Setup {
      val expectedURL = s"${connector.iiUrl}/incorporation-information/subscribe/$txId/regime/$tRegime/subscriber/$tSubscriber?force=true"
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.eq(expectedURL))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      await(connector.cancelSubscription(regId, txId)) shouldBe true
    }
    "make a http DELETE request to Incorporation Information micro-service to register an interest and return a 404" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[NotFoundException](await(connector.cancelSubscription(regId, txId)))
    }
    "not make a http DELETE request to Incorporation Information micro-service to register an interest and return any other 2xx" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(HttpResponse(202))

      intercept[RuntimeException](await(connector.cancelSubscription(regId, txId)))
    }
    "not make a http DELETE request to Incorporation Information micro-service to register an interest and return any 5xx" in new Setup {
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("500", 502, 500)))

      intercept[RuntimeException](await(connector.cancelSubscription(regId, txId)))
    }

    "use the old regime" in new Setup {
      val oldRegime = "ct"
      val expectedURL = s"${connector.iiUrl}/incorporation-information/subscribe/$txId/regime/$oldRegime/subscriber/$tSubscriber?force=true"
      when(mockWSHttp.DELETE[HttpResponse](ArgumentMatchers.eq(expectedURL))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      await(connector.cancelSubscription(regId, txId, useOldRegime = true)) shouldBe true
    }
  }

  "checkNotIncorporated" should {
    "return None" when {
      "the CRN is not there" in new Setup {
        val expectedURL = s"${connector.iiUrl}/incorporation-information/$txId/incorporation-update"
        when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.eq(expectedURL))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(HttpResponse(200))

        await(connector.checkCompanyIncorporated(txId)) shouldBe None
      }

      "on a no content response" in new Setup {
        val expectedURL = s"${connector.iiUrl}/incorporation-information/$txId/incorporation-update"
        when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.eq(expectedURL))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(HttpResponse(204))

        await(connector.checkCompanyIncorporated(txId)) shouldBe None
      }
    }

    "return Some crn" when {
      "the CRN is there" in new Setup {
        val expectedURL = s"${connector.iiUrl}/incorporation-information/$txId/incorporation-update"
        when(mockWSHttp.GET[HttpResponse](ArgumentMatchers.eq(expectedURL))(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(HttpResponse(200, Some(Json.obj("crn" -> "crn"))))

        withCaptureOfLoggingFrom(Logger) { logs =>
          await(connector.checkCompanyIncorporated(txId)) shouldBe Some("crn")

          logs.size shouldBe 1
          logs.head.getMessage shouldBe "STALE_DOCUMENTS_DELETE_WARNING_CRN_FOUND"
        }
      }
    }
  }
}
