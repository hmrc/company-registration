/*
 * Copyright 2019 HM Revenue & Customs
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

import config.MicroserviceAppConfig
import javax.inject.Inject
import connectors.{BusinessRegistrationConnector, BusinessRegistrationNotFoundResponse, BusinessRegistrationSuccessResponse}
import models.{CorporationTaxRegistration, UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsValue, Json}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace


class UserAccessServiceImpl @Inject()(
        val throttleService: ThrottleService,
        val ctService: CorporationTaxRegistrationService,
        val brConnector: BusinessRegistrationConnector,
        val repositories: Repositories,
        val microserviceAppConfig: MicroserviceAppConfig
      ) extends UserAccessService {

  lazy val ctRepository = repositories.cTRepository
  lazy val threshold = microserviceAppConfig.getConfInt("throttle-threshold", throw new Exception("Could not find Threshold in config"))
}

private[services] class MissingRegistration(regId: String) extends NoStackTrace

trait UserAccessService {

  val threshold : Int
  val brConnector : BusinessRegistrationConnector
  val ctRepository : CorporationTaxRegistrationMongoRepository
  val ctService : CorporationTaxRegistrationService
  val throttleService : ThrottleService

  def checkUserAccess(internalId: String)(implicit hc : HeaderCarrier): Future[Either[JsValue,UserAccessSuccessResponse]] = {
    brConnector.retrieveMetadata flatMap {
      case BusinessRegistrationSuccessResponse(metadata) =>
        val now = DateTime.now(DateTimeZone.UTC)
        for {
          _ <- brConnector.updateLastSignedIn(metadata.registrationID, now)
          oCrData <- ctService.retrieveCorporationTaxRegistrationRecord(metadata.registrationID, Some(now))
        } yield {
          oCrData match {
            case Some(crData) => Right(UserAccessSuccessResponse(crData.registrationID, false, hasConfRefs(crData), hasPaymentRefs(crData), crData.verifiedEmail, crData.registrationProgress))
            case _ => throw new MissingRegistration(metadata.registrationID) //todo - after a rejected submission this will always return a failed future - need to delete BR
          }
        }
      case BusinessRegistrationNotFoundResponse =>
        throttleService.checkUserAccess flatMap {
          case false => Future.successful(Left(Json.toJson(UserAccessLimitReachedResponse(limitReached=true))))
          case true => for {
            metaData <- brConnector.createMetadataEntry
            crData <- ctService.createCorporationTaxRegistrationRecord(internalId, metaData.registrationID, "en")
          } yield Right(UserAccessSuccessResponse(crData.registrationID, true, hasConfRefs(crData), hasPaymentRefs(crData), crData.verifiedEmail, crData.registrationProgress))
        }
      case _ => throw new Exception("Something went wrong")
    }
  }

  private[services] def hasConfRefs(doc: CorporationTaxRegistration): Boolean = {
    doc.confirmationReferences.isDefined
  }

  private[services] def hasPaymentRefs(doc: CorporationTaxRegistration): Boolean =
    doc.confirmationReferences.fold(false)(cr => cr.paymentReference.isDefined && cr.paymentAmount.isDefined)
}
