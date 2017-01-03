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

package controllers.test

import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import repositories.{HeldSubmission, HeldSubmissionMongoRepository}
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._

import scala.concurrent.Future

class HeldSubmissionControllerSpec extends UnitSpec with MockitoSugar {

  val mockHeldSubmissionRepo = mock[HeldSubmissionMongoRepository]

  class Setup {
    val controller = new HeldSubmissionController {
      val heldSubmissionRepo = mockHeldSubmissionRepo
    }
  }

  "fetchHeldSubmission" should {

    val registrationId = "testRegId"
    val ackRef = "testAckRef"
    val json = Json.obj("test" -> "ing")

    val heldSubmission = HeldSubmission(registrationId, ackRef, json)

    "return a 200 with a held submission" in new Setup {
      when(mockHeldSubmissionRepo.retrieveSubmissionByRegId(Matchers.eq(registrationId)))
        .thenReturn(Future.successful(Some(heldSubmission)))

      val result = await(controller.fetchHeldSubmission(registrationId)(FakeRequest()))
      status(result) shouldBe 200
      jsonBodyOf(result) shouldBe Json.toJson(heldSubmission)
    }

    "return a 404 if a held submission is not found" in new Setup {
      when(mockHeldSubmissionRepo.retrieveSubmissionByRegId(Matchers.eq(registrationId)))
        .thenReturn(Future.successful(None))

      val result = await(controller.fetchHeldSubmission(registrationId)(FakeRequest()))
      status(result) shouldBe 404
    }
  }
}
