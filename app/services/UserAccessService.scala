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

import connectors.{BusinessRegistrationConnector, BusinessRegistrationNotFoundResponse, BusinessRegistrationSuccessResponse}
import play.api.libs.json.{JsValue, Json}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object UserAccessService extends UserAccessService{
  val bRConnector = BusinessRegistrationConnector
  val cTService = CorporationTaxRegistrationService
  val cTRepository = Repositories.cTRepository
}

trait UserAccessService {

  val bRConnector : BusinessRegistrationConnector
  val cTRepository : CorporationTaxRegistrationMongoRepository
  val cTService : CorporationTaxRegistrationService

  def checkUserAccess(oid: String)(implicit hc : HeaderCarrier): Future[JsValue] = {
    bRConnector.retrieveMetadata flatMap {
      case BusinessRegistrationSuccessResponse(x) => Future.successful(Json.parse(s"""{"registration-id":${x.registrationID},"created":false}"""))
      case BusinessRegistrationNotFoundResponse => for{
        metaData <- bRConnector.createMetadataEntry
        crData <- cTService.createCorporationTaxRegistrationRecord(oid, metaData.registrationID, "en")
      } yield Json.parse(s"""{"registration-id":${metaData.registrationID},"created":true}""")
      case _ => throw new Exception("Something went wrong")
    }
  }
}
