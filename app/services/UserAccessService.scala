/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.mvc.Request
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class UserAccessServiceImpl @Inject()(val throttleService: ThrottleService,
                                      val ctService: CorporationTaxRegistrationService,
                                      val brConnector: BusinessRegistrationConnector,
                                      val repositories: Repositories,
                                      servicesConfig: ServicesConfig
                                     )(implicit val ec: ExecutionContext) extends UserAccessService {

  lazy val ctRepository = repositories.cTRepository
  lazy val threshold = servicesConfig.getConfInt("throttle-threshold", throw new Exception("Could not find Threshold in config"))
}

private[services] class MissingRegistration(regId: String) extends NoStackTrace

trait UserAccessService {

  implicit val ec: ExecutionContext
  val threshold: Int
  val brConnector: BusinessRegistrationConnector
  val ctRepository: CorporationTaxRegistrationMongoRepository
  val ctService: CorporationTaxRegistrationService
  val throttleService: ThrottleService

  def checkUserAccess(internalId: String)(implicit hc: HeaderCarrier): Future[Either[JsValue, UserAccessSuccessResponse]] = {
    brConnector.retrieveMetadata flatMap {
      case BusinessRegistrationSuccessResponse(metadata) =>
        val now = Instant.now
        for {
          _ <- brConnector.updateLastSignedIn(metadata.registrationID, now)
          oCrData <- ctService.retrieveCorporationTaxRegistrationRecord(metadata.registrationID, Some(now))
          crData <- oCrData match {
            case Some(crData) =>
              Future.successful(Right(UserAccessSuccessResponse(crData.registrationID, false, hasConfRefs(crData), hasPaymentRefs(crData), crData.verifiedEmail, crData.registrationProgress)))
            case _ =>
              brConnector.removeMetadata(metadata.registrationID).map { _ =>
                throw new MissingRegistration(metadata.registrationID)
              }
          }
        } yield crData
      case BusinessRegistrationNotFoundResponse =>
        throttleService.checkUserAccess flatMap {
          case false => Future.successful(Left(Json.toJson(UserAccessLimitReachedResponse(limitReached = true))))
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
