/*
 * Copyright 2022 HM Revenue & Customs
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
import helpers.BaseSpec
import models.ConfirmationReferences
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories._
import utils.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TestEndpointControllerSpec extends BaseSpec with LogCapturing {

  implicit val system = ActorSystem("CR")
  implicit val mat = ActorMaterializer()

  val mockThrottleRepository = mock[ThrottleMongoRepository]
  val mockCTRepository = mock[CorporationTaxRegistrationMongoRepository]

  class Setup {
    object Controller extends TestEndpointController {
      val throttleMongoRepository = mockThrottleRepository
      val cTMongoRepository = mockCTRepository
      val bRConnector = mockBusRegConnector
      val submissionService = mockSubmissionService
      val controllerComponents = stubControllerComponents()
      implicit val ec: ExecutionContext = global
    }
  }

  "modifyThrottledUsers" should {

    "return a 200" in new Setup {
      when(mockThrottleRepository.modifyThrottledUsers(ArgumentMatchers.anyString(), ArgumentMatchers.eq(5)))
        .thenReturn(Future.successful(5))

      val result: Future[Result] = Controller.modifyThrottledUsers(5)(FakeRequest())
      status(result) shouldBe OK
      contentAsJson(result).toString() shouldBe """{"users_in":5}"""
    }
  }

  "dropCTCollection" should {

    "return a 200 with a confirmation message" in new Setup {
      when(mockCTRepository.drop(ArgumentMatchers.any())).thenReturn(Future.successful(true))
      when(mockBusRegConnector.dropMetadataCollection(ArgumentMatchers.any()))
        .thenReturn(Future.successful("test message success"))

      val result: Future[Result] = Controller.dropJourneyCollections(FakeRequest())
      status(result) shouldBe OK
      contentAsJson(result).toString() shouldBe """{"message":"CT collection was dropped test message success"}"""
    }

    "return a 200 with an error message when the collection drop was unsuccessful" in new Setup {
      when(mockCTRepository.drop(ArgumentMatchers.any())).thenReturn(Future.successful(false))
      when(mockBusRegConnector.dropMetadataCollection(ArgumentMatchers.any()))
        .thenReturn(Future.successful("test message failed"))

      val result: Future[Result] = Controller.dropJourneyCollections(FakeRequest())
      status(result) shouldBe OK
      contentAsJson(result).toString() shouldBe """{"message":"A problem occurred and the CT Collection could not be dropped test message failed"}"""
    }
  }

  "updateConfirmationRefs" should {

    val registrationId = "testRegId"

    val confirmationRefs = ConfirmationReferences("", "testTransID", Some("testPaymentRef"), Some("12"))

    "return a 200 if the document was successfully updated with a set of confirmation refs" in new Setup {
      when(mockSubmissionService.handleSubmission(eqTo(registrationId), any(), any(), eqTo(false))(any(), any()))
        .thenReturn(Future.successful(confirmationRefs))

      val result: Future[Result] = Controller.updateConfirmationRefs(registrationId)(FakeRequest())
      status(result) shouldBe OK
    }
  }

  "pagerDuty" must {
    "log a pager duty with the message provided" in new Setup {
      val message: String = "test-pager-duty"
      withCaptureOfLoggingFrom(Controller.logger) { logs =>
        val result = Controller.pagerDuty(message)(FakeRequest())
        status(result) shouldBe OK
        logs.head.getMessage shouldBe "[Controller] " + message
      }
    }
  }
}
