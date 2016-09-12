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

import java.util.UUID

import models.HandoffCHData
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class HandoffMongoRepositoryISpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with BeforeAndAfterAll with WithFakeApplication {

  class Setup {
    val repository = new HandoffMongoRepository
//    await(repository.drop)
    await(repository.ensureIndexes)
  }

//  override def afterAll() = new Setup {
//    await(repository.drop)
//  }

  "Handoff repository storage" should {
    "be able to store an empty ch document" in new Setup {
      val randomRegid = UUID.randomUUID().toString
      val data = HandoffCHData(randomRegid)

      val result = await(repository.storeHandoffCHData(data))

      result shouldBe defined

      val chResult = result.get
      chResult.registrationID shouldBe data.registrationID
//      chResult.updated shouldNot be (data.updated)
    }

    "be able to store a simple ch document" in new Setup {
      val randomRegid = UUID.randomUUID().toString
      val json = """{"a":"1", "b":2}"""
      val data = HandoffCHData(randomRegid, ch = Json.parse(json))

      val result = await(repository.storeHandoffCHData(data))

      result shouldBe defined

      val chResult = result.get
      chResult.registrationID shouldBe data.registrationID
//      chResult.updated shouldNot be (data.updated)
      chResult.ch shouldBe Json.parse(json)
    }

    "be able to store and then update a simple ch document" in new Setup {
      val randomRegid = UUID.randomUUID().toString
      val json1 = """{"a":"1", "b":2}"""
      val json2 = """{"a":"2", "c":2}"""
      val data1 = HandoffCHData(randomRegid, ch = Json.parse(json1))
      val data2 = HandoffCHData(randomRegid, ch = Json.parse(json2))

      val result1 = await(repository.storeHandoffCHData(data1))
      result1 shouldBe defined

      val result2 = await(repository.storeHandoffCHData(data2))
      result2 shouldBe defined

      val chResult = result2.get
      chResult.registrationID shouldBe data2.registrationID
      //      chResult.updated shouldNot be (data.updated)
      chResult.ch shouldBe Json.parse(json2)
    }
  }

  "Handoff repository retrieve" should {
    "return None if trying to fetch a handoff that doesn't exist" in new Setup {
      val randomRegid = UUID.randomUUID().toString
      await(repository.fetchHandoffCHData(randomRegid)) shouldBe None
    }

    "be able to fetch a ch document if previously stored" in new Setup {
      val randomRegid = UUID.randomUUID().toString
      val json = """{"a":"1", "b":2}"""
      val data = HandoffCHData(randomRegid, ch = Json.parse(json))

      await(repository.storeHandoffCHData(data)) shouldBe defined

      val result = await(repository.fetchHandoffCHData(randomRegid))

      result shouldBe defined

      val chResult = result.get
      chResult.registrationID shouldBe randomRegid
      chResult.ch shouldBe Json.parse(json)
    }
  }
}
