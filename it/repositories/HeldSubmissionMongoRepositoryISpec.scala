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

import com.fasterxml.jackson.core.JsonParseException
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import reactivemongo.api.commands.DefaultWriteResult
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HeldSubmissionMongoRepositoryISpec extends UnitSpec with ScalaFutures with MongoSpecSupport with BeforeAndAfterEach with BeforeAndAfterAll with WithFakeApplication {

  class Setup {
    val repository = new HeldSubmissionMongoRepository(mongo)
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll() = new Setup {
    await(repository.drop)
  }

  def newId = UUID.randomUUID().toString

  "Held submission repository storage" should {
    "be able to store an simple Json document as a string" in new Setup {
      val randomRegid = newId
      val randomAckid = newId
      val data = Json.obj("bar" -> "foo")

      val beforeCount: Int = await(repository.count)

      val oResult = await(repository.storePartialSubmission(randomRegid, randomAckid, data))

      await(repository.count) shouldBe (beforeCount + 1)

      oResult shouldBe defined

      val result = oResult.get
      result.registrationID shouldBe randomRegid
      result.acknowledgementReference shouldBe randomAckid
      Json.parse(result.partialSubmission) shouldBe data
    }

    "Fail to store a submission with the same registration id" in new Setup {
      val randomRegid = newId
      val data = Json.obj("bar" -> "foo")

      whenReady( repository.storePartialSubmission(randomRegid, newId, data) ) {
        result => { result shouldBe defined }
      }

      val f = repository.storePartialSubmission(randomRegid, newId, data)

      whenReady( f.failed ) {
        ex => ex match {
          case DefaultWriteResult(_, _, _, _, Some(code), Some(message)) => { code shouldBe 11000 }
          case _ => fail(s"Unexpected result - ${ex}")
        }
      }
    }

    "Fail to store a submission with the same ackref id" in new Setup {
      val randomAckid = newId
      val data = Json.obj("bar" -> "foo")

      whenReady( repository.storePartialSubmission(newId, randomAckid, data) ) {
        result => { result shouldBe defined }
      }

      val f = repository.storePartialSubmission(newId, randomAckid, data)

      whenReady( f.failed ) {
        ex => ex match {
          case DefaultWriteResult(_, _, _, _, Some(code), Some(message)) => { code shouldBe 11000 }
          case _ => fail(s"Unexpected result - ${ex}")
        }
      }

    }
  }

  "Retrieving a held submission" should {
    val randomRegid = newId
    val randomAckid = newId

    "Return a held submission by registrations id" in new Setup {
      val heldSubmission = Json.obj("x" -> "y")

      val f: Future[Option[HeldSubmission]] = repository.storePartialSubmission(randomRegid, randomAckid, heldSubmission) flatMap { _ =>
        repository.retrieveSubmissionByRegId(randomRegid)
      }

      val data = await(f)

      data shouldBe defined
      data shouldBe Some(HeldSubmission(randomRegid, randomAckid, heldSubmission))
    }

    "Return a held submission by ack ref" in new Setup {
      val heldSubmission = Json.obj("x" -> "y")

      val f: Future[Option[HeldSubmission]] = repository.storePartialSubmission(randomRegid, randomAckid, heldSubmission) flatMap { _ =>
        repository.retrieveSubmissionByAckRef(randomAckid)
      }

      val data = await(f)

      data shouldBe defined
      data shouldBe Some(HeldSubmission(randomRegid, randomAckid, heldSubmission))
    }

    "Return None when the submission does not exist" in new Setup {
      val data = await( repository.retrieveSubmissionByRegId(randomRegid) )
      data shouldBe None
    }

    "Error if the submission isn't Json" in new Setup {
      await(repository.insert(HeldSubmissionData(randomRegid, randomAckid, "foo" )))

      val f = repository.retrieveSubmissionByRegId(randomRegid)
      intercept[JsonParseException] { await(f) }
    }
  }

  "Clear down held document" should {
    val randomRegid = newId

    "Remove an existing doc if it exists" in new Setup {
      val heldSubmission = Json.obj("x" -> "y")

      val startCount = await(repository.count)

      await(repository.storePartialSubmission(randomRegid, randomRegid, heldSubmission) flatMap { _ =>
        repository.count} ) shouldBe (startCount + 1)

      val result = await(repository.removeHeldDocument(randomRegid))
      result shouldBe true

      await(repository.count) shouldBe startCount
    }

    "Remove an existing doc if it exists and not the collection" in new Setup {
      val randomRegid2 = newId
      val heldSubmission = Json.obj("x" -> "y")

      val startCount = await(repository.count)

      await(repository.storePartialSubmission(randomRegid2, randomRegid2, heldSubmission))
      await(repository.storePartialSubmission(randomRegid, randomRegid, heldSubmission) flatMap { _ =>
        repository.count} ) shouldBe (startCount + 2)

      val result = await(repository.removeHeldDocument(randomRegid))
      result shouldBe true

      await(repository.count) shouldBe startCount + 1
    }

    "Fail to remove a document that's not there" in new Setup {
      val startCount = await(repository.count)

      val result = await(repository.removeHeldDocument(randomRegid))
      result shouldBe false

      await(repository.count) shouldBe startCount
    }
  }
}