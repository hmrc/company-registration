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

package repositories

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import services.ThrottleService
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class ThrottleMongoRepositoryISpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with BeforeAndAfterAll with WithFakeApplication {

  class Setup {
    val service = ThrottleService
    val repository = ThrottleService.throttleMongoRepository
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll() = new Setup {
    await(repository.drop)
  }

  val testKey = "testKey"

  "Throttle repository" should {
    "should be able to get an _id" in new Setup {
      val response = await(repository.update(testKey, 10))
      response shouldBe 1
    }

    "get sequences, one after another from 1 to the end" in new Setup {
      val inputs = 1 to 7
      val outputs = inputs map { _ => await(repository.update(testKey, 10)) }
      outputs shouldBe inputs
    }
  }

  "Throttle service" should {

    "return a 1 when the user count collection is inserted" in new Setup {
      await(service.updateUserCount()) shouldBe 1
    }

    "return a 10 when the user count is at the limit" in new Setup {
      for(i <- 0 to 9){service.updateUserCount()}

      await(service.updateUserCount()) shouldBe 10
    }

    "return the threshold number when the user count is over the limit" in new Setup {
      for(i <- 0 to 15){service.updateUserCount()}

      await(service.updateUserCount()) shouldBe 10
    }
  }
}