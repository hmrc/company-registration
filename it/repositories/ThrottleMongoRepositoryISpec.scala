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

package repositories

import itutil.IntegrationSpecBase
import org.mongodb.scala.bson.BsonDocument
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import services.ThrottleService

class ThrottleMongoRepositoryISpec extends IntegrationSpecBase {

  val additionalConfiguration = Map(
    "schedules.missing-incorporation-job.enabled" -> "false",
    "schedules.metrics-job.enabled" -> "false",
    "schedules.remove-stale-documents-job.enabled" -> "false"
  )
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build()

  class Setup {
    val service = app.injector.instanceOf[ThrottleService]
    val repository = app.injector.instanceOf[ThrottleMongoRepository]
    await(repository.collection.deleteMany(BsonDocument()).toFuture())
    await(repository.ensureIndexes)
    }

  val testKey = "testKey"

  "Throttle repository" must {
    "should be able to get an _id" in new Setup {
      val response = await(repository.update(testKey, 10))
      response mustBe 1
    }

    "get sequences, one after another from 1 to the end" in new Setup {
      val inputs = 1 to 7
      val outputs = inputs map { _ => await(repository.update(testKey, 10)) }
      outputs mustBe inputs
    }
  }

  "Throttle service" must {

    "return true when the user count collection is inserted" in new Setup {
      await(service.checkUserAccess) mustBe true
    }

    "return a true when the user count is updated and is at the limit and then return a false on the next update" in new Setup {
      for(i <- 1 to 9){
        await(service.checkUserAccess)
      }

      await(service.checkUserAccess) mustBe true
      await(service.checkUserAccess) mustBe false
    }

    "return false when the user count is over the limit" in new Setup {
      for(i <- 0 to 15){await(service.checkUserAccess)}

      await(service.checkUserAccess) mustBe false
    }
  }

  "modifyThrottledUsers" must {

    "return the modified users_in value" in new Setup {
      val date = "20-12-2000"
      val usersIn = 10

      await(repository.update(date, 500))

      await(repository.modifyThrottledUsers(date, usersIn)) mustBe 10
    }
  }
}