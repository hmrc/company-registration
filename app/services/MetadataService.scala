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

package services

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import models.Metadata
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{Ok, Created}
import repositories.{Repositories, MetadataRepository}

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object MetadataService extends MetadataService {
  override val metadataRepository = Repositories.metadataRepository
}

trait MetadataService {

  val metadataRepository: MetadataRepository

  def createMetadataRecord(metadata: Metadata): Future[Result] = {
    val newMetadata = metadata.copy(
      registrationID = generateRegistrationId,
      formCreationTimestamp = generateTimestamp(new DateTime())
    )
    metadataRepository.createMetadata(newMetadata).map(res => Created(Json.toJson(res)))
  }

  private def generateRegistrationId: String = {
    //todo: random number gen until we know how to create
    scala.util.Random.nextInt("99999".toInt).toString
  }

  private def generateTimestamp(timeStamp: DateTime) : String = {
    val timeStampFormat = "yyyy-MM-dd'T'HH:mm:ssXXX"
    val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    val format: SimpleDateFormat = new SimpleDateFormat(timeStampFormat)
    format.setTimeZone(UTC)
    format.format(new Date(timeStamp.getMillis))
  }

  def retrieveMetadataRecord(oID: String): Future[Result] = {
    metadataRepository.retrieveMetaData(oID).flatMap{
      case Some(data) => Future.successful(Ok(Json.toJson(data)))
      case _ => createMetadataRecord(Metadata.empty.copy(OID = oID))
    }
  }

  //todo: update function not currently needed - uncomment on later story when required
  //  private[services] def updateMetadataRecord(metadata: Metadata): Future[Result] = {
  //    metadataRepository.updateMetadata(metadata).map(res => Ok(Json.toJson(res)))
  //  }
  //  private[services] def metadataExists(OID: String): Future[Boolean] = {
  //    metadataRepository.retrieveMetaData(OID).map{
  //      case Some(_) => true
  //      case _ => false
  //    }
  //  }
  //  def createMetadata(metadata: Metadata): Future[Result] = {
  //    metadataExists(metadata.OID).flatMap {
  //      if (_) updateMetadataRecord(metadata) else createMetadataRecord(metadata)
  //    }
  //  }
}
