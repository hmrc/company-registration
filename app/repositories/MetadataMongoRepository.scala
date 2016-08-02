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

import models.Metadata
import play.api.Logger
import reactivemongo.api.DB
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

trait MetadataRepository extends Repository[Metadata, BSONObjectID]{
  def createMetadata(metadata: Metadata): Future[Metadata]
  //def updateMetadata(metadata: Metadata): Future[Metadata]
  def retrieveMetaData(oid: String): Future[Option[Metadata]]
  def metadataSelector(oID: String): BSONDocument
}

class MetadataMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Metadata, BSONObjectID](Collections.metadata, mongo, Metadata.formats, ReactiveMongoFormats.objectIdFormats)
  with MetadataRepository {

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    collection.indexesManager.ensure(Index(Seq("OID" -> IndexType.Ascending), name = Some("oidIndex"), unique = true))
    .map(Seq(_))

  override def metadataSelector(oID: String): BSONDocument = BSONDocument(
    "OID" -> BSONString(oID)
  )

  override def createMetadata(metadata: Metadata): Future[Metadata] = {
    collection.insert(metadata).map { res =>
      if (res.hasErrors) {
        Logger.error(s"Failed to store metadata. Error: ${res.errmsg.getOrElse("")} for registration id ${metadata.registrationID}")
      }
      metadata
    }
  }

  override def retrieveMetaData(oID: String): Future[Option[Metadata]] = {
    val selector = metadataSelector(oID)
    collection.find(selector).one[Metadata]
  }

//todo: update function not currently needed - uncomment on later story when required
//  override def updateMetadata(metadata: Metadata): Future[Metadata] = {
//    val selector = metadataSelector(metadata.OID)
//    collection.update(selector, metadata, upsert = false).map{ res =>
//      if(res.hasErrors){
//        Logger.error(s"Failed to update metadata. Error: ${res.errmsg.getOrElse("")} for registration id ${metadata.registrationID}")
//      }
//      metadata
//    }
//  }
}
