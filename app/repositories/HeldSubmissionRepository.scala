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

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsObject, JsValue, Json}
import reactivemongo.api.DB
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID, BSONString, BSONValue, DefaultBSONHandlers}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Logger
import reactivemongo.api.indexes.{Index, IndexType}

import scala.collection.Seq

case class HeldSubmission( regId: String, ackRef: String, submission: JsObject )

object HeldSubmission {
  implicit val formats = Json.format[HeldSubmission]
}

case class HeldSubmissionData(_id: String,
                              acknowledgementReference: String,
                              partialSubmission: String,
                              heldTime: DateTime = HeldSubmissionData.now
                             ) {
  def registrationID = _id
}

object HeldSubmissionData {
  implicit val formats = Json.format[HeldSubmissionData]
  def now = DateTime.now(DateTimeZone.UTC)
}

trait HeldSubmissionRepository extends Repository[HeldSubmissionData, BSONObjectID]{
  def storePartialSubmission(regId: String, ackRef:String, partialSubmission: JsObject): Future[Option[HeldSubmissionData]]
  def retrieveSubmissionByRegId(regId: String): Future[Option[HeldSubmission]]
  def retrieveSubmissionByAckRef(ackRef: String): Future[Option[HeldSubmission]]
}

class HeldSubmissionMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[HeldSubmissionData, BSONObjectID]("held-submission", mongo, HeldSubmissionData.formats, ReactiveMongoFormats.objectIdFormats)
  with HeldSubmissionRepository{

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("acknowledgementReference" -> IndexType.Ascending),
      name = Some("AckRefIndex"), unique = true, sparse = false
    )
  )

  def storePartialSubmission(regId: String, ackRef:String, partialSubmission: JsObject): Future[Option[HeldSubmissionData]] = {

    val data = HeldSubmissionData(
      _id = regId,
      acknowledgementReference = ackRef,
      partialSubmission = partialSubmission.toString()
    )

    collection.insert(data) map {
      case DefaultWriteResult(true, _, _, _, _, _) => {
        Some(data)
      }
      case err => {
        Logger.error(s"Unexpected result : $err")
        None
      }
    }
  }

  private def mapHeldSubmission(mongoData: HeldSubmissionData) : HeldSubmission = {
    val json = Json.parse(mongoData.partialSubmission).as[JsObject]
    HeldSubmission(mongoData.registrationID, mongoData.acknowledgementReference, json)
  }

  def retrieveSubmissionByRegId(regId: String): Future[Option[HeldSubmission]] = {
    val selector = BSONDocument("_id" -> BSONString(regId))
    collection.find(selector).one[HeldSubmissionData] map {
      _ map { mapHeldSubmission( _ ) }
    }
  }

  def retrieveSubmissionByAckRef(ackRef: String): Future[Option[HeldSubmission]] = {
    val selector = BSONDocument("acknowledgementReference" -> BSONString(ackRef))
    collection.find(selector).one[HeldSubmissionData] map {
      _ map { mapHeldSubmission( _ ) }
    }
  }
}
