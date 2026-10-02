/*
 * Copyright 2024 HM Revenue & Customs
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
import models.{BusinessRegistration, CorporationTaxRegistration, UserAccessLimitReachedResponse, UserAccessSuccessResponse}
import play.api.libs.json.{JsValue, Json}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.Logging

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

  lazy val ctRepository: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
  lazy val threshold: Int = servicesConfig.getConfInt("throttle-threshold", throw new Exception("Could not find Threshold in config"))
}

private[services] class MissingRegistration(regId: String) extends NoStackTrace

trait UserAccessService extends Logging {

  implicit val ec: ExecutionContext
  val threshold: Int
  val brConnector: BusinessRegistrationConnector
  val ctRepository: CorporationTaxRegistrationMongoRepository
  val ctService: CorporationTaxRegistrationService
  val throttleService: ThrottleService

  def checkUserAccess(internalId: String)(implicit hc: HeaderCarrier): Future[Either[JsValue, UserAccessSuccessResponse]] = {
    brConnector.retrieveMetadata flatMap {
      case BusinessRegistrationSuccessResponse(metadata) =>
        updateExisting(metadata, internalId)
      case BusinessRegistrationNotFoundResponse =>
        createNew(internalId)
      case unexpected =>
        throw new Exception(s"[UserAccessService][checkUserAccess] Unexpected result when trying to retrieve metadata: $unexpected")
    }
  }

  // 1. How many users are getting the failure? Is there a pattern?
  // 2. Is the data missing for some reason (not seeing any deletions)
  // or could the reg ID be inaccurate for some reason?
  // 3. If has been deleted, should we create new?
  // If creating new, do we need to run 'throttleService.checkUserAccess'?
  //  we currently do when creating for new metadata and doc, we don't currently for existing metadata and doc
  private def updateExisting(metadata: BusinessRegistration, internalId: String)(implicit hc: HeaderCarrier): Future[Either[JsValue, UserAccessSuccessResponse]] = {
    val now = Instant.now
    for {
      _ <- brConnector.updateLastSignedIn(metadata.registrationID, now)
      oCrData <- ctService.retrieveCorporationTaxRegistrationRecord(metadata.registrationID, Some(now)).recover {
        case _: NoSuchElementException =>
          val errorMg = s"[UserAccessService][checkUserAccess] Unable to find data in corporation-tax-registration-information for internal ID '$internalId' and registration ID '${metadata.registrationID}'"
          logger.warn(errorMg)
          throw new NoSuchElementException(errorMg)
      }
      crData <- oCrData match {
        case Some(crData) =>
          Future.successful(Right(UserAccessSuccessResponse(crData.registrationID, created = false, confRefs = hasConfRefs(crData), paymentRefs = hasPaymentRefs(crData), crData.verifiedEmail, crData.registrationProgress)))
        case _ =>
          brConnector.removeMetadata(metadata.registrationID).map { _ =>
            throw new MissingRegistration(metadata.registrationID)
          }
      }
    } yield crData

  }

  private def createNew(internalId: String)(implicit hc: HeaderCarrier): Future[Either[JsValue, UserAccessSuccessResponse]] = {
    throttleService.checkUserAccess flatMap {
      case false => Future.successful(Left(Json.toJson(UserAccessLimitReachedResponse(limitReached = true))))
      case true => for {
        metaData <- brConnector.createMetadataEntry
        crData <- ctService.createCorporationTaxRegistrationRecord(internalId, metaData.registrationID, "en")
      } yield Right(UserAccessSuccessResponse(crData.registrationID, created = true, confRefs = hasConfRefs(crData), paymentRefs = hasPaymentRefs(crData), crData.verifiedEmail, crData.registrationProgress))
    }
  }

  private[services] def hasConfRefs(doc: CorporationTaxRegistration): Boolean = {
    doc.confirmationReferences.isDefined
  }

  private[services] def hasPaymentRefs(doc: CorporationTaxRegistration): Boolean =
    doc.confirmationReferences.fold(false)(cr => cr.paymentReference.isDefined && cr.paymentAmount.isDefined)
}
