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

package controllers

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import connectors.AuthConnector
import fixtures.AuthFixture
import helpers.SCRSSpec
import models.TradingDetails
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CorporationTaxRegistrationService, MetricsService, TradingDetailsService}
import mocks.{MockMetricsService, SCRSMocks}
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class TradingDetailsControllerSpec extends UnitSpec with MockitoSugar with SCRSMocks with AuthFixture {

  implicit val system = ActorSystem("CR")
  implicit val materializer = ActorMaterializer()

  val mockTradingDetailsService = mock[TradingDetailsService]

  class Setup {
    object TestController extends TradingDetailsController {
      val tradingDetailsService = mockTradingDetailsService
      val auth = mockAuthConnector
      val resourceConn = mockCTDataRepository
      override val metricsService = MockMetricsService
    }
  }

  val regID = UUID.randomUUID.toString


  "retrieveTradingDetails" should {
    "retrieve a 200 - Ok and a Json package of TradingDetails" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.any()))
        .thenReturn(Future.successful(Some(regID -> validAuthority.ids.internalId)))

      when(mockTradingDetailsService.retrieveTradingDetails(Matchers.eq(regID)))
        .thenReturn(Future.successful(Some(TradingDetails("true"))))

      val result = TestController.retrieveTradingDetails(regID)(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(TradingDetails("true"))
    }

    "return a 404 - Not Found if the record does not exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.any()))
        .thenReturn(Future.successful(Some(regID -> validAuthority.ids.internalId)))

      when(mockTradingDetailsService.retrieveTradingDetails(Matchers.eq(regID)))
        .thenReturn(Future.successful(None))

      val result = TestController.retrieveTradingDetails(regID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe Json.parse(s"""{"statusCode":"404","message":"Could not find trading details record"}""")
    }

    "return a 403 - Forbidden if the user cannot be authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      when(mockCTDataRepository.getInternalId(Matchers.any())).thenReturn(Future.successful(None))

      val result = TestController.retrieveTradingDetails(regID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 - Forbidden if the user is not authorised" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.any())).thenReturn(Future.successful(Some("invalidRegID" -> "invalidID")))

      val result = TestController.retrieveTradingDetails(regID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 - Not found when an authority is found but nothing is returned from" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.any())).thenReturn(Future.successful(None))

      val result = TestController.retrieveTradingDetails(regID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }

  "updateTradingDetails" should {
    "return a 200 - Ok and a company details response if a record is updated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.any()))
        .thenReturn(Future.successful(Some(regID -> validAuthority.ids.internalId)))

      when(mockTradingDetailsService.updateTradingDetails(Matchers.eq("testRegID"), Matchers.eq(TradingDetails("true"))))
        .thenReturn(Future.successful(Some(TradingDetails("true"))))

      val request = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result = call(TestController.updateTradingDetails("testRegID"), request)
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(TradingDetails("true"))
    }

    "return a 404 - Not Found if the record to update does not exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.any()))
        .thenReturn(Future.successful(Some(regID -> validAuthority.ids.internalId)))

      when(mockTradingDetailsService.updateTradingDetails(Matchers.eq("testRegID"), Matchers.eq(TradingDetails("true"))))
        .thenReturn(Future.successful(None))

      val request = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result = call(TestController.updateTradingDetails("testRegID"), request)
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe Json.parse(s"""{"statusCode":"404","message":"Could not find trading details record"}""")
    }

    "return a 403 - Forbidden if the user cannot be authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      when(mockCTDataRepository.getInternalId(Matchers.any())).thenReturn(Future.successful(Some("testRegID" -> "testID")))

      val request = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result = call(TestController.updateTradingDetails(regID), request)
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 - Forbidden if the user is not authorised" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.any())).thenReturn(Future.successful(Some("invalidRegID" -> "invalidID")))

      val request = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result = call(TestController.updateTradingDetails(regID), request)
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 - Not found when an authority is found but nothing is updated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.any())).thenReturn(Future.successful(None))

      when(mockTradingDetailsService.updateTradingDetails(Matchers.eq("testRegID"), Matchers.eq(TradingDetails("true"))))
        .thenReturn(Future.successful(None))

      val request = FakeRequest().withBody(Json.toJson(TradingDetails("true")))
      val result = call(TestController.updateTradingDetails(regID), request)
      status(result) shouldBe NOT_FOUND
    }
  }
}
