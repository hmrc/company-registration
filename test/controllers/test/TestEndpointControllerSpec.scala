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

import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import play.api.test.FakeRequest
import repositories.ThrottleMongoRepository
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import play.api.test.Helpers._

import scala.concurrent.Future

class TestEndpointControllerSpec extends UnitSpec with MockitoSugar {

  val mockThrottleRepository = mock[ThrottleMongoRepository]

  class Setup {
    val controller = new TestEndpointController {
      val throttleMongoRepository = mockThrottleRepository
    }
  }

  "modifyThrottledUsers" should {

    "return a 200" in new Setup {
      when(mockThrottleRepository.modifyThrottledUsers(Matchers.anyString(), Matchers.eq(5)))
        .thenReturn(Future.successful(5))

      val result = controller.modifyThrottledUsers(5)(FakeRequest())
      status(result) shouldBe OK
    }
  }
}
