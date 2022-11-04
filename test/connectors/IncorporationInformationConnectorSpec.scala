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

import ch.qos.logback.classic.Level
import fixtures.BusinessRegistrationFixture
import helpers.BaseSpec
import models.IncorpStatus
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.LogCapturingHelper

import java.time.{Instant, LocalDate}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class IncorporationInformationConnectorSpec extends BaseSpec with BusinessRegistrationFixture with LogCapturingHelper {

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

  "registerInterest" must {
    "make a http POST request to Incorporation Information micro-service to register an interest and return 202" in new Setup {
      when(mockWSHttp.POST[JsValue, Boolean](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      await(Connector.registerInterest(regId, txId)) mustBe true
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest when future fails" in new Setup {
      when(mockWSHttp.POST[JsValue, Boolean](ArgumentMatchers.anyString(), ArgumentMatchers.any[JsValue](), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException("bang")))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        intercept[RuntimeException](await(Connector.registerInterest(regId, txId)))

        logs.containsMsg(Level.ERROR, s"[Connector][registerInterest] Exception of type 'RuntimeException' was thrown for regId: '$regId' and txId: '$txId'")
      }
    }
  }

  "cancelSubscription" must {
    "make a http DELETE request to Incorporation Information micro-service to register an interest and return 200" in new Setup {
      val expectedURL = s"${Connector.iiUrl}/incorporation-information/subscribe/$txId/regime/$tRegime/subscriber/$tSubscriber?force=true"
      when(mockWSHttp.DELETE[Boolean](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      await(Connector.cancelSubscription(regId, txId)) mustBe true
    }
    "not make a http DELETE request to Incorporation Information micro-service to register an interest and return any 5xx" in new Setup {
      when(mockWSHttp.DELETE[Boolean](ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException("bang")))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        intercept[RuntimeException](await(Connector.cancelSubscription(regId, txId)))

        logs.containsMsg(Level.ERROR, s"[Connector][cancelSubscription] Exception of type 'RuntimeException' was thrown for regId: '$regId' and txId: '$txId'")
      }
    }

    "use the old regime" in new Setup {
      val oldRegime = "ct"
      val expectedURL = s"${Connector.iiUrl}/incorporation-information/subscribe/$txId/regime/$oldRegime/subscriber/$tSubscriber?force=true"
      when(mockWSHttp.DELETE[Boolean](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      await(Connector.cancelSubscription(regId, txId, useOldRegime = true)) mustBe true
    }
  }

  "checkNotIncorporated" must {
    "return None" when {
      "the CRN is not there" in new Setup {
        val expectedURL = s"${Connector.iiUrl}/incorporation-information/$txId/incorporation-update"
        when(mockWSHttp.GET[Option[String]](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(None))

        await(Connector.checkCompanyIncorporated(txId)) mustBe None
      }
    }

    "return Some crn" when {
      "the CRN is there" in new Setup {
        val expectedURL = s"${Connector.iiUrl}/incorporation-information/$txId/incorporation-update"
        when(mockWSHttp.GET[Option[String]](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(Some("crn")))

        await(Connector.checkCompanyIncorporated(txId)) mustBe Some("crn")
      }
    }

    "throw exception on unexpected failed future" in new Setup {

      val expectedURL = s"${Connector.iiUrl}/incorporation-information/$txId/incorporation-update"
      when(mockWSHttp.GET[Option[String]](ArgumentMatchers.eq(expectedURL), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("bang")))

      withCaptureOfLoggingFrom(Connector.logger) { logs =>
        intercept[Exception](await(Connector.checkCompanyIncorporated(txId)))

        logs.containsMsg(Level.ERROR, s"[Connector][checkCompanyIncorporated] Exception of type 'Exception' was thrown for txId: '$txId'")
      }
    }
  }
}
