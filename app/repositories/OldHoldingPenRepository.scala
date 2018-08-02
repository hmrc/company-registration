/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json
import reactivemongo.api.DB
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

case class HeldSubmissionData(_id: String,
                              acknowledgementReference: Option[String],
                              partialSubmission: Option[String],
                              heldTime: Option[DateTime] = Some(DateTime.now())
                             )

object HeldSubmissionData {
  implicit val formats = Json.format[HeldSubmissionData]
}

class OldHoldingPenRepository(mongo: () => DB) extends
  ReactiveRepository[HeldSubmissionData, BSONObjectID](
    "held-submission", mongo, HeldSubmissionData.formats, ReactiveMongoFormats.objectIdFormats){

  def dropDatabase(implicit ec: ExecutionContext): Unit = {
    find().flatMap{res =>
      if(res.nonEmpty) {
        val docsToPrint = res.map(_.copy(partialSubmission = Some("cannot be printed to logs")))
        Future.successful(Logger.info(s"[oldHoldingPenRepository] Documents still exist in 'held-submission' no drop will take place here are the existing docs $docsToPrint"))
      } else {
        collection.drop(true).map(dropped => Logger.info(s"[dbDropped] 'held-submission' is $dropped"))
      }
    }.recoverWith{
      case GenericDatabaseException(_, Some(26)) => Future.successful(Logger.info("[oldHoldingPenRepository] dropDatabase cannot find 'held-submission' to drop no drop took place"))
      case ex: Exception                         => Future.successful(Logger.warn(s"[oldHoldingPenRepository] dropDatabase failed with ${ex.getMessage}"))
    }
  }
}