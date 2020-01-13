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

package auth

import helpers.BaseSpec
import mocks.AuthorisationMocks
import play.api.test.FakeRequest
import play.api.mvc.Results.Ok
import repositories.MissingCTDocument
import uk.gov.hmrc.auth.core.{BearerTokenExpired, InvalidBearerToken, MissingBearerToken, SessionRecordNotFound}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

class AuthorisedActionsSpec extends BaseSpec with AuthorisationMocks {

  trait Setup {
    val authorisedActions = new AuthorisedActions with BaseController {
      override val authConnector = mockAuthConnector
      override val resource = mockResource
    }
  }

  val registrationID = "reg-12345"
  val internalId = "int-12345"
  val otherInternalID = "other-int-12345"

  "AuthorisedAction" should {

    val block = Future.successful(Ok)
    val request = FakeRequest()

    "run the block and return a 200 when the user is authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      val result = await(authorisedActions.AuthorisedAction(registrationID).async(block)(request))
      status(result) shouldBe 200
    }

    "return a 404 when fetching the internal id from the resource returns a None" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.failed(new MissingCTDocument("hfbhdbf")))

      val result = await(authorisedActions.AuthorisedAction(registrationID).async(block)(request))
      status(result) shouldBe 404
    }

    "return a 403 when the request is authorised but is not allowed to access the resource" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(otherInternalID))

      val result = await(authorisedActions.AuthorisedAction(registrationID).async(block)(request))
      status(result) shouldBe 403
    }

    "return a 401 when there are no active session tokens in the request" in new Setup {

      List(
        MissingBearerToken(),
        BearerTokenExpired(),
        InvalidBearerToken(),
        SessionRecordNotFound()
      ) foreach { ex =>
        mockAuthorise(Future.failed(ex))

        val result = await(authorisedActions.AuthorisedAction(registrationID).async(block)(request))
        status(result) shouldBe 401
      }
    }
  }
}
