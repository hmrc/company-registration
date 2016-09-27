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

package services

import org.joda.time.DateTime
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import repositories.{Repositories, ThrottleMongoRepository}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._

import scala.concurrent.Future

class ThrottleServiceSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  val mockThrottleMongoRepository = mock[ThrottleMongoRepository]

  trait Setup {
    val service = new ThrottleService {
      val throttleMongoRepository = mockThrottleMongoRepository
      val dateTime = DateTime.parse("2000-02-01")
      val threshold = 10
    }
  }

  "ThrottleService" should {

    "use the correct repository" in {
      ThrottleService.throttleMongoRepository shouldBe Repositories.throttleRepository
    }
  }

  "getCurrentDay" should {

    "return the current day" in new Setup {
      service.getCurrentDay shouldBe "2000/02/01"
    }
  }

  "updateUserCount" should {

    "return a 1 when updating user count on a new collection" in new Setup {
      when(mockThrottleMongoRepository.update(Matchers.eq("2000/02/01"), Matchers.eq(10), Matchers.eq(false)))
        .thenReturn(Future.successful(1))

      await(service.updateUserCount()) shouldBe 1
    }

    "return a 10 when user threshold has been reached" in new Setup {
      when(mockThrottleMongoRepository.update(Matchers.eq("2000/02/01"), Matchers.eq(10), Matchers.eq(false)))
        .thenReturn(Future.successful(10))
      when(mockThrottleMongoRepository.compensate(Matchers.eq("2000/02/01"), Matchers.eq(10)))
        .thenReturn(Future.successful(10))

      await(service.updateUserCount()) shouldBe 10
    }

    "return a 10 when user threshold is over the limit" in new Setup {
      when(mockThrottleMongoRepository.update(Matchers.eq("2000/02/01"), Matchers.eq(10), Matchers.eq(false)))
        .thenReturn(Future.successful(15))
      when(mockThrottleMongoRepository.compensate(Matchers.eq("2000/02/01"), Matchers.eq(10)))
        .thenReturn(Future.successful(10))

      await(service.updateUserCount()) shouldBe 10
    }

    "return a 1 when updating the user count on a new day" in new Setup {

    }
  }
}