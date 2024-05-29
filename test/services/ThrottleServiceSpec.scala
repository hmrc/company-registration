/*
 * Copyright 2024 HM Revenue & Customs
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

import config.MicroserviceAppConfig
import helpers.BaseSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.test.Helpers._
import repositories.{Repositories, ThrottleMongoRepository}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ThrottleServiceSpec extends BaseSpec {

  val mockThrottleMongoRepository: ThrottleMongoRepository = mock[ThrottleMongoRepository]
  val mockRepositories: Repositories = mock[Repositories]
  val mockConfig: MicroserviceAppConfig = mock[MicroserviceAppConfig]

  trait Setup {
    val service: ThrottleService = new ThrottleService(mockRepositories, mockConfig, stubControllerComponents()) {
      override lazy val throttleMongoRepository: ThrottleMongoRepository = mockThrottleMongoRepository
      override def date: LocalDate = LocalDate.parse("2000-02-01")
      override lazy val threshold = 10
    }
  }

  "getCurrentDay" must {
    "return the current day" in new Setup {
      service.getCurrentDay mustBe "2000-02-01"
    }
  }

  "updateUserCount" must {

    "return true when updating user count on a new collection" in new Setup {
      when(mockThrottleMongoRepository.update(ArgumentMatchers.eq("2000-02-01"), ArgumentMatchers.eq(10), ArgumentMatchers.eq(false)))
        .thenReturn(Future.successful(1))

      await(service.checkUserAccess) mustBe true
    }

    "return true when user threshold is reached" in new Setup {
      when(mockThrottleMongoRepository.update(ArgumentMatchers.eq("2000-02-01"), ArgumentMatchers.eq(10), ArgumentMatchers.eq(false)))
        .thenReturn(Future.successful(10))
      when(mockThrottleMongoRepository.compensate(ArgumentMatchers.eq("2000-02-01"), ArgumentMatchers.eq(10)))
        .thenReturn(Future.successful(10))

      await(service.checkUserAccess) mustBe true
    }

    "return false when user threshold is over the limit" in new Setup {
      when(mockThrottleMongoRepository.update(ArgumentMatchers.eq("2000-02-01"), ArgumentMatchers.eq(10), ArgumentMatchers.eq(false)))
        .thenReturn(Future.successful(15))
      when(mockThrottleMongoRepository.compensate(ArgumentMatchers.eq("2000-02-01"), ArgumentMatchers.eq(10)))
        .thenReturn(Future.successful(10))

      await(service.checkUserAccess) mustBe false
    }
  }
}
