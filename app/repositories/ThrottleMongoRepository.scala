/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{FindOneAndUpdateOptions, ReturnDocument, Updates}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ThrottleMongoRepository @Inject()(mongo: MongoComponent)(implicit val ec: ExecutionContext)
  extends PlayMongoRepository[UserCount](
    mongoComponent = mongo,
    collectionName = "throttle",
    domainFormat = UserCount.formats,
    indexes = Seq()
  ) {

  private def update(selector: Bson, modifier: Bson): Future[UserCount] =
    collection.findOneAndUpdate(
      selector,
      modifier,
      FindOneAndUpdateOptions()
        .upsert(true)
        .returnDocument(ReturnDocument.AFTER)
    ).toFuture()

  def update(date: String, threshold: Int, compensate: Boolean = false): Future[Int] = {
    val selector = equal("_id", date)
    val modifier = compensate match {
      case true =>
        Updates.combine(
          Updates.inc("users_in", -1),
          Updates.inc("users_blocked", 1),
          Updates.set("threshold", threshold)
        )
      case false =>
        Updates.combine(
          Updates.inc("users_in", 1),
          Updates.set("threshold", threshold)
        )
    }

    update(selector, modifier).map(_.users_in)
  }

  def compensate(date: String, threshold: Int): Future[Int] =
    update(date, threshold, compensate = true)

  def modifyThrottledUsers(date: String, usersIn: Int): Future[Int] = {
    val selector = equal("_id", date)
    val modifier = set("users_in", usersIn)

    update(selector, modifier).map(_.users_in)
  }
}
