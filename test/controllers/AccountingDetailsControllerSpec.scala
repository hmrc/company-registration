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
import fixtures.{AccountingDetailsFixture, AuthFixture}
import helpers.SCRSSpec
import mocks.MockMetricsService
import models.{AccountPrepDetails, ErrorResponse}
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.PrepareAccountService

import scala.concurrent.Future

class AccountingDetailsControllerSpec extends SCRSSpec with AuthFixture with AccountingDetailsFixture {

  val mockPrepareAccountService = mock[PrepareAccountService]

  trait Setup {
    val controller = new AccountingDetailsController {
      override val auth = mockAuthConnector
      override val resourceConn = mockCTDataRepository
      override val accountingDetailsService = mockAccountingDetailsService
      override val metricsService = MockMetricsService
      override val prepareAccountService = mockPrepareAccountService
    }
  }

  val registrationID = "12345"

  "retrieveAccountingDetails" should {
    "return a 200 with accounting details in the json body when authorised" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.anyString()))
        .thenReturn(Future.successful(Some(registrationID -> validAuthority.ids.internalId)))
      AccountingDetailsServiceMocks.retrieveAccountingDetails(registrationID, Some(validAccountingDetails))

      when(mockPrepareAccountService.updateEndDate(Matchers.any()))
        .thenReturn(Future.successful(Some(AccountPrepDetails())))

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(validAccountingDetailsResponse)
    }


    "return a 404 when the user is authorised but accounting details cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.anyString()))
        .thenReturn(Future.successful(Some(registrationID -> validAuthority.ids.internalId)))
      AccountingDetailsServiceMocks.retrieveAccountingDetails(registrationID, None)

      when(mockPrepareAccountService.updateEndDate(Matchers.any()))
        .thenReturn(Future.successful(Some(AccountPrepDetails())))

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe ErrorResponse.accountingDetailsNotFound
    }

    "return a 404 when the auth resource cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.anyString()))
        .thenReturn(Future.successful(None))
      AccountingDetailsServiceMocks.retrieveAccountingDetails(registrationID, None)

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }


    "return a 403 when the user is unauthorised to access the record" in new Setup {
      val authority = validAuthority.copy(ids = validAuthority.ids.copy(internalId = "notAuthorisedID"))
      AuthenticationMocks.getCurrentAuthority(Some(authority))
      when(mockCTDataRepository.getInternalId(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> "testID")))

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      when(mockCTDataRepository.getInternalId(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> "testID")))

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }
  }

  "updateAccountingDetails" should {
    "return a 200 with accounting details in the json body when authorised" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.anyString()))
        .thenReturn(Future.successful(Some(registrationID -> validAuthority.ids.internalId)))
      AccountingDetailsServiceMocks.updateAccountingDetails(registrationID, Some(validAccountingDetails))

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(validAccountingDetailsResponse)
    }
  }

    "return a 404 when the user is authorised but accounting details cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId("testID", Some(registrationID -> validAuthority.ids.internalId))
      AccountingDetailsServiceMocks.updateAccountingDetails(registrationID, None)

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe NOT_FOUND
    }

    "return a 400 when the auth resource cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId("testID", None)

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> (validAuthority.ids.internalId + "123"))))

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getInternalId("testID", Some("testRegID" -> "testID"))

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe FORBIDDEN
    }
}
