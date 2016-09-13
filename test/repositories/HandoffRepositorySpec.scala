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

import helpers.MongoMocks
import models.HandoffCHData
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.BeforeAndAfter
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.mongo.MongoSpecSupport
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.commands._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class HandoffRepositorySpec extends UnitSpec with MongoSpecSupport with MongoMocks with MockitoSugar with BeforeAndAfter {

  class MockedHandoffRepository extends HandoffMongoRepository {
    override lazy val collection = mockCollection()
  }

  val repository = new MockedHandoffRepository()

  before {
    reset(repository.collection)
  }

  "Update handoff data" should {

    "Store an empty document with CH data" in {

      val randomRegid = UUID.randomUUID().toString

      val filterCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val updateCaptor = ArgumentCaptor.forClass(classOf[HandoffCHData])

      val data = HandoffCHData(randomRegid)

      setupAnyUpdateOn(repository.collection, fails = false)

      val dataResult = await(repository.storeHandoffCHData(data))

      dataResult shouldBe defined

      verify(repository.collection).update(filterCaptor.capture(), updateCaptor.capture(), any[WriteConcern], anyBoolean(), anyBoolean())(any(), any(), any[ExecutionContext])

      val insertedData = updateCaptor.getValue
      insertedData._id shouldBe randomRegid
    }

    "Store a document with simple CH data" in {

      val randomRegid = UUID.randomUUID().toString

      val filterCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val updateCaptor = ArgumentCaptor.forClass(classOf[HandoffCHData])

      val json = """{"a":"1", "b":2}"""
      val data = HandoffCHData(randomRegid, ch = Json.parse(json))

      setupAnyUpdateOn(repository.collection, fails = false)

      val dataResult = await(repository.storeHandoffCHData(data))

      dataResult shouldBe defined

      verify(repository.collection).update(filterCaptor.capture(), updateCaptor.capture(), any[WriteConcern], anyBoolean(), anyBoolean())(any(), any(), any[ExecutionContext])

      val insertedData = updateCaptor.getValue
      insertedData._id shouldBe randomRegid
      insertedData.ch shouldBe Json.parse(json)
    }

    "Handle a failure to store a document" in {

      val randomRegid = UUID.randomUUID().toString

      val filterCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      val updateCaptor = ArgumentCaptor.forClass(classOf[HandoffCHData])

      val json = """{"a":"1", "b":2}"""
      val data = HandoffCHData(randomRegid, ch = Json.parse(json))

      setupAnyUpdateOn(repository.collection, fails = true)

      val dataResult = await(repository.storeHandoffCHData(data))

      dataResult shouldNot be (defined)
    }
  }
}
