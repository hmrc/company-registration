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
import models.{UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import play.api.libs.json.{JsValue, Json}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object UserAccessService extends UserAccessService with ServicesConfig {
  val bRConnector = BusinessRegistrationConnector
  val cTService = CorporationTaxRegistrationService
  val cTRepository = Repositories.cTRepository
  val throttleService = ThrottleService
  val threshold = getConfInt("throttle-threshold", throw new Exception("Could not find Threshold in config"))
}

trait UserAccessService {

  val threshold : Int
  val bRConnector : BusinessRegistrationConnector
  val cTRepository : CorporationTaxRegistrationMongoRepository
  val cTService : CorporationTaxRegistrationService
  val throttleService : ThrottleService

  def checkUserAccess(oid: String)(implicit hc : HeaderCarrier): Future[Either[JsValue,JsValue]] = {
    bRConnector.retrieveMetadata flatMap {
      case BusinessRegistrationSuccessResponse(x) => Future.successful(Right(Json.toJson(UserAccessSuccessResponse(x.registrationID,created = false))))
      case BusinessRegistrationNotFoundResponse =>
          throttleService.checkUserAccess flatMap {
            case false => Future.successful(Left(Json.toJson(UserAccessLimitReachedResponse(limitReached=true))))
            case true => for{
              metaData <- bRConnector.createMetadataEntry
              crData <- cTService.createCorporationTaxRegistrationRecord(oid, metaData.registrationID, "en")
            } yield Right(Json.parse(s"""{"registration-id":"${metaData.registrationID}","created":true}"""))
          }
      case _ => throw new Exception("Something went wrong")
    }
  }
}
