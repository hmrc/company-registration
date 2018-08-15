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

package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import connectors.AuthConnector
import fixtures.AuthFixture
import helpers.{BaseSpec, SCRSSpec}
import models.{UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.{CorporationTaxRegistrationService, MetricsService, UserAccessService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.test.Helpers._
import mocks.{AuthorisationMocks, MockMetricsService, SCRSMocks}
import uk.gov.hmrc.auth.core.MissingBearerToken

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class UserAccessControllerSpec extends BaseSpec with AuthorisationMocks {

  implicit val system = ActorSystem("CR")
  implicit val materializer = ActorMaterializer()

  val mockUserAccessService = mock[UserAccessService]

  implicit val hc = HeaderCarrier()

  trait Setup {
    val controller = new UserAccessController {
      override val userAccessService = mockUserAccessService
      override val metricsService = MockMetricsService
      override val authConnector = mockAuthClientConnector
    }
  }

  val internalId = "int-12345"

  "checkUserAccess" should {

    "return a unauthorised status code when user is not in session" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result = controller.checkUserAccess(FakeRequest())
      status(result) shouldBe UNAUTHORIZED
    }

    "return a 200" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockUserAccessService.checkUserAccess(anyString())(any()))
        .thenReturn(Future.successful(Right(UserAccessSuccessResponse("123", false, false, false))))

      val result = controller.checkUserAccess(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(UserAccessSuccessResponse("123", false, false, false))
    }

    "return a 429" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))
      when(mockUserAccessService.checkUserAccess(anyString())(any()))
        .thenReturn(Future.successful(Left(Json.toJson(UserAccessLimitReachedResponse(limitReached = true)))))

      val result = controller.checkUserAccess(FakeRequest())
      status(result) shouldBe TOO_MANY_REQUESTS
    }
  }

}
