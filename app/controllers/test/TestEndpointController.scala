/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.test


import connectors.BusinessRegistrationConnector
import helpers.DateHelper
import models.ConfirmationReferences
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories._
import services.SubmissionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class TestEndpointControllerImpl @Inject()(val submissionService: SubmissionService,
                                           val bRConnector: BusinessRegistrationConnector,
                                           val repositories: Repositories,
                                           val controllerComponents: ControllerComponents
                                          )(implicit val ec: ExecutionContext) extends TestEndpointController {
  lazy val throttleMongoRepository = repositories.throttleRepository
  lazy val cTMongoRepository = repositories.cTRepository
}

trait TestEndpointController extends BackendBaseController with Logging {
  implicit val ec: ExecutionContext
  val throttleMongoRepository: ThrottleMongoRepository
  val cTMongoRepository: CorporationTaxRegistrationMongoRepository
  val bRConnector: BusinessRegistrationConnector
  val submissionService: SubmissionService

  def modifyThrottledUsers(usersIn: Int) = Action.async {
    implicit request =>
      val date = DateHelper.getCurrentDay
      throttleMongoRepository.modifyThrottledUsers(date, usersIn).map(x => Ok(Json.parse(s"""{"users_in" : $x}""")))
  }

  def dropCTCollection = {
    cTMongoRepository.drop map {
      case true => "CT collection was dropped"
      case false => "A problem occurred and the CT Collection could not be dropped"
    }
  }

  def dropJourneyCollections = Action.async {
    implicit request =>
      for {
        cTDrop <- dropCTCollection
        bRDrop <- bRConnector.dropMetadataCollection
      } yield {
        Ok(Json.parse(s"""{"message":"$cTDrop $bRDrop"}"""))
      }
  }

  def updateSubmissionStatusToHeld(registrationId: String) = Action.async {
    implicit request =>
      cTMongoRepository.updateSubmissionStatus(registrationId, "Held").map(_ => Ok)
  }

  def updateConfirmationRefs(registrationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      val confirmationRefs = ConfirmationReferences("", "testOnlyTransactionId", Some("testOnlyPaymentRef"), Some("12"))
      submissionService.handleSubmission(registrationId, "testAuthProviderId", confirmationRefs, isAdmin = false)(hc, request)
        .map(_ => Ok)
  }

  def removeTaxRegistrationInformation(registrationId: String) = Action.async {
    implicit request =>
      cTMongoRepository.removeTaxRegistrationInformation(registrationId) map (if (_) Ok else BadRequest)
  }

  def pagerDuty(name: String) = Action.async {
    implicit request =>
      logger.error(name)
      Future.successful(Ok)
  }
}
