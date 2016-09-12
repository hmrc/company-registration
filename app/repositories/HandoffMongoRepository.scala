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

import models.HandoffCHData
import play.api.libs.json.JsValue
import reactivemongo.api.DB
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID, BSONString}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Logger


trait HandoffRepository extends Repository[HandoffCHData, BSONObjectID]{
  def fetchHandoffCHData(id: String): Future[Option[HandoffCHData]]
  def storeHandoffCHData(data: HandoffCHData): Future[Option[HandoffCHData]]
}

class HandoffMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[HandoffCHData, BSONObjectID]("handoff-ch-data", mongo, HandoffCHData.formats, ReactiveMongoFormats.objectIdFormats)
  with HandoffRepository{

  private def idSelector(id: String): BSONDocument = BSONDocument("_id" -> BSONString(id) )

  def fetchHandoffCHData(id: String): Future[Option[HandoffCHData]] = {

    collection.find(idSelector(id)).one[HandoffCHData]
  }

  def storeHandoffCHData(data: HandoffCHData): Future[Option[HandoffCHData]] = {

    val dataToStore = data.copy(updated = HandoffCHData.now)

    collection.update(idSelector(data._id), dataToStore, upsert=true) map {
      case UpdateWriteResult(true, _, _, _, _, _, _, _) => Some(dataToStore)
      case UpdateWriteResult(false, _, _, _, _, _, _, errorMsg) => {
        Logger.error(s"Failed to update the company registration handoff data due to $errorMsg")
        None
      }
    }
  }
}
