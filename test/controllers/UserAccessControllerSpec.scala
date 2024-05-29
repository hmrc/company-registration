/*
 * Copyright 2024 HM Revenue & Customs
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

import helpers.BaseSpec
import mocks.{AuthorisationMocks, MockMetricsService}
import models.{UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.UserAccessService
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserAccessControllerSpec extends BaseSpec with AuthorisationMocks {

  implicit val system: ActorSystem = ActorSystem("CR")
  implicit val materializer: Materializer = Materializer(system)

  val mockUserAccessService: UserAccessService = mock[UserAccessService]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup {
    val controller = new UserAccessController(
      mockAuthConnector,
      MockMetricsService,
      mockUserAccessService,
      stubControllerComponents()
    )
  }

  val internalId = "int-12345"

  "checkUserAccess" must {

    "return a unauthorised status code when user is not in session" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result: Future[Result] = controller.checkUserAccess(FakeRequest())
      status(result) mustBe UNAUTHORIZED
    }

    "return a 200" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))
      when(mockUserAccessService.checkUserAccess(anyString())(any()))
        .thenReturn(Future.successful(Right(UserAccessSuccessResponse("123", created = false, confRefs = false, paymentRefs = false))))

      val result: Future[Result] = controller.checkUserAccess(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(UserAccessSuccessResponse("123", created = false, confRefs = false, paymentRefs = false))
    }

    "return a 429" in new Setup {
      mockAuthorise(Future.successful(Some(internalId)))
      mockGetInternalId(Future.successful(internalId))
      when(mockUserAccessService.checkUserAccess(anyString())(any()))
        .thenReturn(Future.successful(Left(Json.toJson(UserAccessLimitReachedResponse(limitReached = true)))))

      val result: Future[Result] = controller.checkUserAccess(FakeRequest())
      status(result) mustBe TOO_MANY_REQUESTS
    }

    "return a 403 when no internalId retrieved from Auth" in new Setup {
      mockAuthorise(Future.successful(None))

      val result: Future[Result] = controller.checkUserAccess(FakeRequest())
      status(result) mustBe FORBIDDEN
    }
  }

}
