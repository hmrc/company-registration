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
import fixtures.{AccountingDetailsFixture, AuthFixture}
import helpers.{BaseSpec, SCRSSpec}
import mocks.{AuthorisationMocks, MockMetricsService, SCRSMocks}
import models.{AccountPrepDetails, ErrorResponse}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, OneAppPerTest}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.PrepareAccountService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AccountingDetailsControllerSpec extends BaseSpec with AccountingDetailsFixture with AuthorisationMocks {

  val mockPrepareAccountService = mock[PrepareAccountService]

  trait Setup {
    val controller = new AccountingDetailsController {
      override val authConnector = mockAuthClientConnector
      override val resource = mockResource
      override val accountingDetailsService = mockAccountingDetailsService
      override val metricsService = MockMetricsService
      override val prepareAccountService = mockPrepareAccountService
    }
  }

  val registrationID = "12345"
  val internalId = "int-12345"

  val accountingDetailsResponseJson = Json.toJson(validAccountingDetailsResponse)

  "retrieveAccountingDetails" should {

    "return a 200 with accounting details in the js on body when authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      AccountingDetailsServiceMocks.retrieveAccountingDetails(registrationID, Some(validAccountingDetails))

      when(mockPrepareAccountService.updateEndDate(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(AccountPrepDetails())))

      val result = await(controller.retrieveAccountingDetails(registrationID)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe accountingDetailsResponseJson
    }


    "return a 404 when the user is authorised but accounting details cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      AccountingDetailsServiceMocks.retrieveAccountingDetails(registrationID, None)

      when(mockPrepareAccountService.updateEndDate(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(AccountPrepDetails())))

      val result = await(controller.retrieveAccountingDetails(registrationID)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
      contentAsJson(result) shouldBe ErrorResponse.accountingDetailsNotFound
    }
  }

  "updateAccountingDetails" should {

    val request = FakeRequest().withBody(Json.toJson(validAccountingDetails))

    "return a 200 with accounting details in the json body when authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      AccountingDetailsServiceMocks.updateAccountingDetails(registrationID, Some(validAccountingDetails))

      val result = await(controller.updateAccountingDetails(registrationID)(request))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe accountingDetailsResponseJson
    }

    "return a 404 when the user is authorised but accounting details cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      AccountingDetailsServiceMocks.updateAccountingDetails(registrationID, None)

      val result = await(controller.updateAccountingDetails(registrationID)(request))
      status(result) shouldBe NOT_FOUND
    }
  }
}
