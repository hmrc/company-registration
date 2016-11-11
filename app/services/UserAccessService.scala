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
import models.{CorporationTaxRegistration, UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import play.api.libs.json.{JsValue, Json}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

object UserAccessService extends UserAccessService with ServicesConfig {
  val brConnector = BusinessRegistrationConnector
  val ctService = CorporationTaxRegistrationService
  val ctRepository = Repositories.cTRepository
  val throttleService = ThrottleService
  //$COVERAGE-OFF$
  val threshold = getConfInt("throttle-threshold", throw new Exception("Could not find Threshold in config"))
  //$COVERAGE-ON$
}

private[services] class MissingRegistration(regId: String) extends NoStackTrace

trait UserAccessService {

  val threshold : Int
  val brConnector : BusinessRegistrationConnector
  val ctRepository : CorporationTaxRegistrationMongoRepository
  val ctService : CorporationTaxRegistrationService
  val throttleService : ThrottleService

  def checkUserAccess(oid: String)(implicit hc : HeaderCarrier): Future[Either[JsValue,UserAccessSuccessResponse]] = {
    brConnector.retrieveMetadata flatMap {
      case BusinessRegistrationSuccessResponse(metadata) =>
        createResponse(metadata.registrationID, false) map { Right(_) }
      case BusinessRegistrationNotFoundResponse =>
        throttleService.checkUserAccess flatMap {
          case false => Future.successful(Left(Json.toJson(UserAccessLimitReachedResponse(limitReached=true))))
          case true => for{
            metaData <- brConnector.createMetadataEntry
            crData <- ctService.createCorporationTaxRegistrationRecord(oid, metaData.registrationID, "en")
            result <- createResponse(metaData.registrationID, true)
          } yield Right(result)
        }
      case _ => throw new Exception("Something went wrong")
    }
  }


  private[services] def createResponse(regId: String, created: Boolean): Future[UserAccessSuccessResponse] = {
    ctService.retrieveCorporationTaxRegistrationRecord(regId) flatMap {
      case Some(doc) => {
        Future.successful(UserAccessSuccessResponse(regId, created, hasConfRefs(doc), doc.verifiedEmail))
      }
      case None => Future.failed(new MissingRegistration(regId))
    }
  }

  private[services] def hasConfRefs(doc: CorporationTaxRegistration): Boolean = {
    doc.confirmationReferences.fold(false)(_=>true)
  }
}
