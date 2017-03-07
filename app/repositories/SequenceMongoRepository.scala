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

import models.Sequence
import play.api.libs.json.JsValue
import reactivemongo.api.DB
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait SequenceRepository extends Repository[Sequence, BSONObjectID]{
  def getNext(sequenceID: String): Future[Int]
}

class SequenceMongoRepository(mongo: () => DB)
  extends ReactiveRepository[Sequence, BSONObjectID]("sequence", mongo, Sequence.formats, ReactiveMongoFormats.objectIdFormats)
  with SequenceRepository{

  def getNext(sequenceID: String): Future[Int] = {
    val selector = BSONDocument("_id" -> sequenceID)
    val modifier = BSONDocument("$inc" -> BSONDocument("seq" -> 1))

    collection.findAndUpdate(selector, modifier, fetchNewObject = true, upsert = true) map {
      _.result[JsValue] match {
        case None => -1
        case Some(res) => (res \ "seq").as[Int]
      }
    }
  }
}

