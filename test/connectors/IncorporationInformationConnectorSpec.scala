/*
 * Copyright 2018 HM Revenue & Customs
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
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

class IncorporationInformationConnectorSpec extends UnitSpec with MockitoSugar with SCRSMocks with BusinessRegistrationFixture {

  val tRegime = "testRegime"
  val tSubscriber = "testSubscriber"

  trait Setup {
    val connector = new IncorporationInformationConnector {
      override val url = "testUrl"
      override val http = mockWSHttp
      override val regime = tRegime
      override val subscriber = tSubscriber
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

  "createMetadataEntry" should {
    "make a http POST request to Incorporation Information micro-service to register an interest and return 202" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.anyString(), Matchers.any[JsValue](), Matchers.any())
        (Matchers.any(), Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(HttpResponse(202))

      await(connector.registerInterest(regId, txId)) shouldBe true
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any other 2xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.anyString(), Matchers.any[JsValue](), Matchers.any())
        (Matchers.any(), Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(HttpResponse(200))

      intercept[RuntimeException](await(connector.registerInterest(regId, txId)))
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any 4xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.anyString(), Matchers.any[JsValue](), Matchers.any())
        (Matchers.any(), Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(HttpResponse(400))

      intercept[RuntimeException](await(connector.registerInterest(regId, txId)))
    }
    "not make a http POST request to Incorporation Information micro-service to register an interest and return any 5xx" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.anyString(), Matchers.any[JsValue](), Matchers.any())
        (Matchers.any(), Matchers.any(), Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(HttpResponse(500))

      intercept[RuntimeException](await(connector.registerInterest(regId, txId)))
    }
  }

}
