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
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.HeldSubmissionMongoRepository
import services.RegistrationHoldingPenService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future


class HeldControllerSpec extends SCRSSpec with AuthFixture {

  implicit val system = ActorSystem("CR")
  implicit val materializer = ActorMaterializer()

  implicit val hc = HeaderCarrier()

  trait Setup {
    val controller = new HeldController {
      override val service: RegistrationHoldingPenService = mockRegHoldingPen
      override val heldRepo: HeldSubmissionMongoRepository = mockHeldSubRepo
      override val auth: AuthConnector = mockAuthConnector
      override val resourceConn = mockCTDataRepository
    }
  }


  val regId = "1234"
  val timestamp = "2016-12-31T12:00:00.000Z"
  val dateTime = DateTime.parse(timestamp)

  "fetchHeldSubmissionTime" should {
    "return a 200 response along with a submission time" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(controller.heldRepo.retrieveHeldSubmissionTime(Matchers.any())).thenReturn(Future.successful(Some(dateTime)))

      val result = await(controller.fetchHeldSubmissionTime(regId)(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.toJson(dateTime)
    }

    "return a 404 (Not found) response" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(controller.heldRepo.retrieveHeldSubmissionTime(Matchers.any())).thenReturn(Future.successful(None))

      val result = await(controller.fetchHeldSubmissionTime(regId)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
    }

    "return a Forbidden response when an unauthenticated user tries to fetch a held submission time" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)

      val result = await(controller.fetchHeldSubmissionTime(regId)(FakeRequest()))
      status(result) shouldBe FORBIDDEN
    }
  }


  "deleteSubmissionData" should {
    "return a 200 response when a user is logged in and their rejected submission data is deleted" in new Setup {
      AuthorisationMocks.mockSuccessfulAuthorisation(regId, validAuthority)
      when(controller.service.deleteRejectedSubmissionData(Matchers.any())(Matchers.any())).thenReturn(Future.successful(true))

      val result = await(controller.deleteSubmissionData(regId)(FakeRequest()))
      status(result) shouldBe OK
    }

    "return a 404 (Not found) response when a user's rejected submission data is not found" in new Setup {
      AuthorisationMocks.mockAuthResourceNotFound(validAuthority)
      when(controller.service.deleteRejectedSubmissionData(Matchers.any())(Matchers.any())).thenReturn(Future.successful(false))

      val result = await(controller.deleteSubmissionData(regId)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
    }

    "return a Forbidden response when an unauthenticated user tries to delete submission data" in new Setup {
      AuthorisationMocks.mockNotAuthorised(regId, validAuthority)

      val result = await(controller.deleteSubmissionData(regId)(FakeRequest()))
      status(result) shouldBe FORBIDDEN
    }
  }



}
