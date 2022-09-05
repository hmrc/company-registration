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
import akka.stream.Materializer
import com.mongodb.MongoException
import com.mongodb.client.result.DeleteResult
import helpers.BaseSpec
import models.{ConfirmationReferences, CorporationTaxRegistration}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.mongodb.scala.{MongoCollection, SingleObservable}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories._
import utils.LogCapturing

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class TestEndpointControllerSpec extends BaseSpec with LogCapturing {

  implicit val system = ActorSystem("CR")
  implicit val mat = Materializer(system)

  val mockThrottleRepository = mock[ThrottleMongoRepository]
  val mockCTRepository = mock[CorporationTaxRegistrationMongoRepository]
  val mockCTCollection = mock[MongoCollection[CorporationTaxRegistration]]
  val mockDeleteResult = mock[SingleObservable[DeleteResult]]

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

  "modifyThrottledUsers" must {

    "return a 200" in new Setup {
      when(mockThrottleRepository.modifyThrottledUsers(ArgumentMatchers.anyString(), ArgumentMatchers.eq(5)))
        .thenReturn(Future.successful(5))

      val result: Future[Result] = Controller.modifyThrottledUsers(5)(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result).toString() mustBe """{"users_in":5}"""
    }
  }

  "dropCTCollection" must {

    "return a 200 with a confirmation message" in new Setup {
      when(mockCTRepository.collection).thenReturn(mockCTCollection)
      when(mockCTCollection.deleteMany(ArgumentMatchers.any())).thenReturn(mockDeleteResult)
      when(mockDeleteResult.toFuture()).thenReturn(Future.successful(DeleteResult.acknowledged(1)))
      when(mockBusRegConnector.dropMetadataCollection(ArgumentMatchers.any()))
        .thenReturn(Future.successful("test message success"))

      val result: Future[Result] = Controller.dropJourneyCollections(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result).toString() mustBe """{"message":"CT collection was dropped test message success"}"""
    }

    "return a 200 with an error message when the collection drop was unsuccessful" in new Setup {
      when(mockCTRepository.collection).thenReturn(mockCTCollection)
      when(mockCTCollection.deleteMany(ArgumentMatchers.any())).thenReturn(mockDeleteResult)
      when(mockDeleteResult.toFuture()).thenReturn(Future.failed(new MongoException("bang")))
      when(mockBusRegConnector.dropMetadataCollection(ArgumentMatchers.any()))
        .thenReturn(Future.successful("test message failed"))

      val result: Future[Result] = Controller.dropJourneyCollections(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result).toString() mustBe """{"message":"A problem occurred and the CT Collection could not be dropped test message failed"}"""
    }
  }

  "updateConfirmationRefs" must {

    val registrationId = "testRegId"

    val confirmationRefs = ConfirmationReferences("", "testTransID", Some("testPaymentRef"), Some("12"))

    "return a 200 if the document was successfully updated with a set of confirmation refs" in new Setup {
      when(mockSubmissionService.handleSubmission(eqTo(registrationId), any(), any(), eqTo(false))(any(), any()))
        .thenReturn(Future.successful(confirmationRefs))

      val result: Future[Result] = Controller.updateConfirmationRefs(registrationId)(FakeRequest())
      status(result) mustBe OK
    }
  }

  "pagerDuty" must {
    "log a pager duty with the message provided" in new Setup {
      val message: String = "test-pager-duty"
      withCaptureOfLoggingFrom(Controller.logger) { logs =>
        val result = Controller.pagerDuty(message)(FakeRequest())
        status(result) mustBe OK
        logs.head.getMessage mustBe "[Controller] " + message
      }
    }
  }
}
