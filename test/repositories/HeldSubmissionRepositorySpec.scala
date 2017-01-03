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

package repositories

import java.util.UUID

import helpers.MongoMocks
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.{Mockito, ArgumentCaptor}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.commands._
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class HeldSubmissionRepositorySpec extends UnitSpec with MongoSpecSupport with MongoMocks with MockitoSugar with BeforeAndAfter {

  class MockedHeldSubmissionRepository extends HeldSubmissionMongoRepository {
    override lazy val collection = mockCollection()
    override def indexes: Seq[Index] = Seq()
  }

  val repository = new MockedHeldSubmissionRepository()

  before {
    reset(repository.collection)
  }

  def newId() = UUID.randomUUID().toString

  "Insert handoff data" should {

    "Store a new held submission document" in {

      val submission = """{"foo":"bar"}"""
      val json = Json.obj("foo" -> "bar")

      val data = HeldSubmissionData(newId, newId, submission)

      setupAnyInsertOn(repository.collection, fails = false)

      val dataResult = await(repository.storePartialSubmission(data.registrationID, data.acknowledgementReference, json))

      dataResult shouldBe defined

      val insertCaptor = ArgumentCaptor.forClass(classOf[HeldSubmissionData])

      verify(repository.collection).insert(insertCaptor.capture(), any[WriteConcern])(any(), any[ExecutionContext])

      val insertedData = insertCaptor.getValue
      insertedData._id shouldBe data.registrationID
      insertedData.partialSubmission shouldBe submission
    }

    "Return None if the insert failed but the Future was successful" in {

      val submission = """{"foo":"bar"}"""
      val json = Json.obj("foo" -> "bar")

      val data = HeldSubmissionData(newId, newId, submission)

      setupAnyInsertOn(repository.collection, fails = true)

      val dataResult = await(repository.storePartialSubmission(data.registrationID, data.acknowledgementReference, json))

      dataResult shouldBe None
    }
  }
}
