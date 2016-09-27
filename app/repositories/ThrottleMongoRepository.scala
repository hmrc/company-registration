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

import models.UserCount
import play.api.libs.json.JsValue
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait ThrottleRepository extends Repository[UserCount, BSONObjectID]{
  def update(date: String, threshold: Int, compensate: Boolean) : Future[Int]
  def compensate(date: String, threshold: Int): Future[Int]
}

class ThrottleMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[UserCount, BSONObjectID]("throttle", mongo, UserCount.formats, ReactiveMongoFormats.objectIdFormats)
  with ThrottleRepository {

  def update(date: String, threshold: Int, compensate: Boolean = false): Future[Int] = {
    val selector = BSONDocument("_id" -> date)
    val modifier = compensate match {
      case true => BSONDocument("$inc" -> BSONDocument("users_in" -> -1, "users_blocked" -> 1), "$set" -> BSONDocument("threshold" -> threshold))
      case false => BSONDocument("$inc" -> BSONDocument("users_in" -> 1), "$set" -> BSONDocument("threshold" -> threshold))
    }

    collection.findAndUpdate(selector, modifier, fetchNewObject = true, upsert = true) map {
      _.result[JsValue] match {
        case None => -1
        case Some(res) => (res \ "users_in").as[Int]
      }
    }
  }

  def compensate(date: String, threshold: Int): Future[Int] = {
    update(date, threshold, compensate = true)
  }
}
