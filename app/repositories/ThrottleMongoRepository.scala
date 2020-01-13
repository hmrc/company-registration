/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import models.UserCount
import play.api.Logger
import play.api.libs.json.JsValue
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.bson._
import reactivemongo.play.json.ImplicitBSONHandlers.BSONDocumentWrites
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ThrottleRepository {
  def update(date: String, threshold: Int, compensate: Boolean) : Future[Int]
  def compensate(date: String, threshold: Int): Future[Int]
}

class ThrottleMongoRepo @Inject()(mongo: ReactiveMongoComponent) {
  Logger.info("Creating CorporationTaxRegistrationMongoRepository")

  val repo = new ThrottleMongoRepository(mongo.mongoConnector.db)
}

class ThrottleMongoRepository(mongo: () => DB)
  extends ReactiveRepository[UserCount, BSONObjectID]("throttle", mongo, UserCount.formats, ReactiveMongoFormats.objectIdFormats)
  with ThrottleRepository {
  Logger.info("Creating ThrottleMongoRepository")

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

  def modifyThrottledUsers(date: String, usersIn: Int): Future[Int] = {
    val selector = BSONDocument("_id" -> date)
    val modifier = BSONDocument("$set" -> BSONDocument("users_in" -> usersIn))

    collection.findAndUpdate(selector, modifier, fetchNewObject = true, upsert = true) map {
      _.result[JsValue] match {
        case None => -1
        case Some(res) => (res \ "users_in").as[Int]
      }
    }
  }
}
