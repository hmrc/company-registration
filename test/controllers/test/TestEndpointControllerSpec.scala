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

package controllers.test

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import connectors.BusinessRegistrationConnector
import models.ConfirmationReferences
import org.mockito.ArgumentMatchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import repositories._
import services.CorporationTaxRegistrationService
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._
import play.api.test.Helpers._

import scala.concurrent.Future

class TestEndpointControllerSpec extends UnitSpec with MockitoSugar {

  implicit val system = ActorSystem("CR")
  implicit val mat = ActorMaterializer()

  val mockThrottleRepository = mock[ThrottleMongoRepository]
  val mockCTRepository = mock[CorporationTaxRegistrationMongoRepository]
  val mockBusRegConnector = mock[BusinessRegistrationConnector]
  val mockHeldRepository = mock[HeldSubmissionRepository]
  val mockCTService = mock[CorporationTaxRegistrationService]
  val mockStateRepository = mock[StateDataMongoRepository]

  class Setup {
    val controller = new TestEndpointController {
      val throttleMongoRepository = mockThrottleRepository
      val cTMongoRepository = mockCTRepository
      val bRConnector = mockBusRegConnector
      val heldRepository = mockHeldRepository
      val cTService = mockCTService
      val stateRepo = mockStateRepository
    }
  }

  "modifyThrottledUsers" should {

    "return a 200" in new Setup {
      when(mockThrottleRepository.modifyThrottledUsers(ArgumentMatchers.anyString(), ArgumentMatchers.eq(5)))
        .thenReturn(Future.successful(5))

      val result = await(controller.modifyThrottledUsers(5)(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result).toString() shouldBe """{"users_in":5}"""
    }
  }

  "dropCTCollection" should {

    "return a 200 with a confirmation message" in new Setup {
      when(mockCTRepository.drop(ArgumentMatchers.any())).thenReturn(Future.successful(true))
      when(mockBusRegConnector.dropMetadataCollection(ArgumentMatchers.any()))
        .thenReturn(Future.successful("test message success"))

      val result = await(controller.dropJourneyCollections(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result).toString() shouldBe """{"message":"CT collection was dropped test message success"}"""
    }

    "return a 200 with an error message when the collection drop was unsuccessful" in new Setup {
      when(mockCTRepository.drop(ArgumentMatchers.any())).thenReturn(Future.successful(false))
      when(mockBusRegConnector.dropMetadataCollection(ArgumentMatchers.any()))
        .thenReturn(Future.successful("test message failed"))

      val result = await(controller.dropJourneyCollections(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result).toString() shouldBe """{"message":"A problem occurred and the CT Collection could not be dropped test message failed"}"""
    }
  }

  "getHeldData" should {

    val registrationId = "testRegId"
    val ackRef = "testAckRef"

    val heldSubmission = HeldSubmission(registrationId, ackRef, Json.obj("test" -> "ing"))

    "return a 200 and a valid held submission when one is found" in new Setup {
      when(mockHeldRepository.retrieveSubmissionByRegId(ArgumentMatchers.eq(registrationId)))
        .thenReturn(Future.successful(Some(heldSubmission)))

      val result = await(controller.fetchHeldData(registrationId)(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.toJson(heldSubmission)
    }

    "return a 404 when a held submission is not found" in new Setup {
      when(mockHeldRepository.retrieveSubmissionByRegId(ArgumentMatchers.eq(registrationId)))
        .thenReturn(Future.successful(None))

      val result = await(controller.fetchHeldData(registrationId)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
    }
  }

  "storeHeldData" should {

    val registrationId = "testRegId"
    val ackRef = "testAckRef"

    val request = Json.obj("test" -> "ing")

    val heldSubmission = HeldSubmissionData(registrationId, ackRef, Json.obj("test" -> "ing").toString())

    "return a 200 when it is stored successfully" in new Setup {
      when(mockHeldRepository.storePartialSubmission(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(heldSubmission)))

      val result = await(call(controller.storeHeldData(registrationId, ackRef), FakeRequest().withJsonBody(request)))
      status(result) shouldBe OK

    }

    "return a 400 when a problem occurred when storing the request" in new Setup {
      when(mockHeldRepository.storePartialSubmission(ArgumentMatchers.eq(registrationId), ArgumentMatchers.eq(ackRef), ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      val result = await(call(controller.storeHeldData(registrationId, ackRef), FakeRequest().withJsonBody(request)))
      status(result) shouldBe BAD_REQUEST

    }
  }

  "updateConfirmationRefs" should {

    val registrationId = "testRegId"

    val confirmationRefs = ConfirmationReferences("", "testTransID", Some("testPaymentRef"), Some("12"))

    "return a 200 if the document was successfully updated with a set of confirmation refs" in new Setup {
      when(mockCTService.handleSubmission(ArgumentMatchers.eq(registrationId), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(confirmationRefs))

      val result = await(controller.updateConfirmationRefs(registrationId)(FakeRequest()))
      status(result) shouldBe OK
    }
  }

  "updateTimepoint" should {

    val timepoint = "12345"

    "return a 200" in new Setup {
      when(mockStateRepository.updateTimepoint(ArgumentMatchers.eq(timepoint)))
        .thenReturn(Future.successful(timepoint))

      val result = await(controller.updateTimePoint(timepoint)(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.toJson(timepoint)
    }
  }
}
