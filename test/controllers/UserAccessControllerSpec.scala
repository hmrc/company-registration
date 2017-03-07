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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import connectors.AuthConnector
import fixtures.AuthFixture
import helpers.SCRSSpec
import models.{UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.{CorporationTaxRegistrationService, MetricsService, UserAccessService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier
import mocks.MockMetricsService

import scala.concurrent.Future

class UserAccessControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SCRSSpec with AuthFixture{

  val mockUserAccessService = mock[UserAccessService]

  implicit val hc = HeaderCarrier()

  trait Setup {
    val controller = new UserAccessController {
      override val userAccessService = mockUserAccessService
      override val auth = mockAuthConnector
      override val metricsService = MockMetricsService
    }
  }


  "checkUserAccess" should {

    "return a forbidden status code" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      status(controller.checkUserAccess(FakeRequest())) shouldBe FORBIDDEN
    }

    "return a 200" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockUserAccessService.checkUserAccess(anyString())(any()))
        .thenReturn(Future.successful(Right(UserAccessSuccessResponse("123", false, false))))

      val result = controller.checkUserAccess(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(UserAccessSuccessResponse("123", false, false))
    }

    "return a 429" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockUserAccessService.checkUserAccess(anyString())(any()))
        .thenReturn(Future.successful(Left(Json.toJson(UserAccessLimitReachedResponse(limitReached = true)))))

      val result = controller.checkUserAccess(FakeRequest())
      status(result) shouldBe TOO_MANY_REQUEST
    }
  }

}
