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

import javax.inject.Inject

import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{DB, ReadPreference}
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID, BSONString}
import reactivemongo.play.json.ImplicitBSONHandlers.BSONDocumentWrites
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

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

private class DeletionFailure(val message: String) extends NoStackTrace

class HeldSubmissionRepo @Inject()(mongo: ReactiveMongoComponent) {
  val db = mongo.mongoConnector.db
  val store = new HeldSubmissionMongoRepository(db)
}

trait HeldSubmissionRepository extends Repository[HeldSubmissionData, BSONObjectID]{
  def storePartialSubmission(regId: String, ackRef:String, partialSubmission: JsObject): Future[Option[HeldSubmissionData]]
  def retrieveSubmissionByRegId(regId: String): Future[Option[HeldSubmission]]
  def retrieveSubmissionByAckRef(ackRef: String): Future[Option[HeldSubmission]]
  def removeHeldDocument(regId: String): Future[Boolean]
  def retrieveHeldSubmissionTime(regId: String): Future[Option[DateTime]]
}

class HeldSubmissionMongoRepository(mongo: () => DB)
  extends ReactiveRepository[HeldSubmissionData, BSONObjectID]("held-submission", mongo, HeldSubmissionData.formats, ReactiveMongoFormats.objectIdFormats)
  with HeldSubmissionRepository {

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
      _ map { mapHeldSubmission }
    }
  }

  override def retrieveHeldSubmissionTime(regId: String): Future[Option[DateTime]] = {
    val selector = BSONDocument("_id" -> BSONString(regId))
    collection.find(selector).one[HeldSubmissionData] map (_.map(_.heldTime))
  }

  def retrieveSubmissionByAckRef(ackRef: String): Future[Option[HeldSubmission]] = {
    val selector = BSONDocument("acknowledgementReference" -> BSONString(ackRef))
    collection.find(selector).one[HeldSubmissionData] map {
      _ map { mapHeldSubmission }
    }
  }

  def removeHeldDocument(regId: String): Future[Boolean] = {
    val logPrefix = "[HeldSubmissionRepository] [removeHeldDocument]"
    val selector = BSONDocument("_id" -> BSONString(regId))
    collection.remove(selector) flatMap {
      case DefaultWriteResult(true, 1, _, _, _, _) => Future.successful(true)
      case DefaultWriteResult(true, 0, _, _, _, _) => {
        Logger.warn(s"[HeldSubmissionRepository] [removeHeldDocument] Didn't delete missing held submission for ${regId}")
        Logger.error("FAILED_DES_TOPUP")
        Future.successful(false)
      }
      case unknown => {
        //$COVERAGE-OFF$
        Logger.error(s"${logPrefix} Unexpected error trying to - ${unknown}")
        Future.failed(new DeletionFailure(unknown.toString))
        //$COVERAGE-ON$
      }
    }
  }

  def getAllHeldDocsP : Future[Seq[HeldSubmission]] =  {
    collection.find(BSONDocument()).cursor[HeldSubmissionData](ReadPreference.primary).collect[Seq]() map(_ map mapHeldSubmission)
  }

  def getAllHeldDocsS : Future[Seq[HeldSubmission]] =  {
    collection.find(BSONDocument()).cursor[HeldSubmissionData](ReadPreference.secondary).collect[Seq]() map(_ map mapHeldSubmission)
  }
}
