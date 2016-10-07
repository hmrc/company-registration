/*
 * Copyright 2016 HM Revenue & Customs
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

import connectors.BusinessRegistrationConnector
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import repositories.{CorporationTaxRegistrationMongoRepository, ThrottleMongoRepository}
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import play.api.test.Helpers._

import scala.concurrent.Future

class TestEndpointControllerSpec extends UnitSpec with MockitoSugar {

  val mockThrottleRepository = mock[ThrottleMongoRepository]
  val mockCTRepository = mock[CorporationTaxRegistrationMongoRepository]
  val mockBusRegConnector = mock[BusinessRegistrationConnector]

  class Setup {
    val controller = new TestEndpointController {
      val throttleMongoRepository = mockThrottleRepository
      val cTMongoRepository = mockCTRepository
      val bRConnector = mockBusRegConnector
    }
  }

  "modifyThrottledUsers" should {

    "return a 200" in new Setup {
      when(mockThrottleRepository.modifyThrottledUsers(Matchers.anyString(), Matchers.eq(5)))
        .thenReturn(Future.successful(5))

      val result = await(controller.modifyThrottledUsers(5)(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result).toString() shouldBe """{"users_in":5}"""
    }
  }

  "dropCTCollection" should {

    "return a 200 with a confirmation message" in new Setup {
      when(mockCTRepository.drop(Matchers.any())).thenReturn(Future.successful(true))
      when(mockBusRegConnector.dropMetadataCollection(Matchers.any()))
        .thenReturn(Future.successful("test message success"))

      val result = await(controller.dropJourneyCollections(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result).toString() shouldBe """{"message":"CT collection was dropped test message success"}"""
    }

    "return a 200 with an error message when the collection drop was unsuccessful" in new Setup {
      when(mockCTRepository.drop(Matchers.any())).thenReturn(Future.successful(false))
      when(mockBusRegConnector.dropMetadataCollection(Matchers.any()))
        .thenReturn(Future.successful("test message failed"))

      val result = await(controller.dropJourneyCollections(FakeRequest()))
      status(result) shouldBe OK
      jsonBodyOf(result).toString() shouldBe """{"message":"A problem occurred and the CT Collection could not be dropped test message failed"}"""
    }
  }
}
