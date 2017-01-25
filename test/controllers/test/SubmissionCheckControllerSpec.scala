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

import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import services.RegistrationHoldingPenService
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.mockito.Matchers.any

import scala.concurrent.Future

class SubmissionCheckControllerSpec extends UnitSpec with MockitoSugar {

  val mockRegHoldingPenService = mock[RegistrationHoldingPenService]

  class Setup {
    val controller = new SubmissionCheckController {
      val service = mockRegHoldingPenService
    }
  }

  "triggerSubmissionCheck" should {

    "return a 200 when a successful future is supplied" in new Setup {
      when(mockRegHoldingPenService.updateNextSubmissionByTimepoint(any()))
        .thenReturn(Future.successful("any string"))

      val result = await(controller.triggerSubmissionCheck(FakeRequest()))
      status(result) shouldBe 200
      bodyOf(result) shouldBe "any string"
    }

    "return a 500 with the exception message when a failed future is supplied" in new Setup {
      when(mockRegHoldingPenService.updateNextSubmissionByTimepoint(any()))
        .thenReturn(Future.failed(new Exception("ex message")))

      val result = await(controller.triggerSubmissionCheck(FakeRequest()))
      status(result) shouldBe 500
      bodyOf(result) shouldBe "An error has occurred during the submission - ex message"
    }
  }
}
