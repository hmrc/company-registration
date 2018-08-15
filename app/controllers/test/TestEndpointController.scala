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

package controllers.test


import connectors.BusinessRegistrationConnector
import helpers.DateHelper
import javax.inject.Inject
import models.ConfirmationReferences
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import repositories._
import services.{CorporationTaxRegistrationService, SubmissionService}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TestEndpointControllerImpl @Inject()(
                                            val submissionService: SubmissionService,
                                            val bRConnector: BusinessRegistrationConnector,
                                            val repositories: Repositories
      ) extends TestEndpointController {
  val throttleMongoRepository = repositories.throttleRepository
  val cTMongoRepository = repositories.cTRepository
  val stateRepo = repositories.stateDataRepository
}

trait TestEndpointController extends BaseController {

  val throttleMongoRepository: ThrottleMongoRepository
  val cTMongoRepository: CorporationTaxRegistrationMongoRepository
  val bRConnector: BusinessRegistrationConnector
  val submissionService: SubmissionService
  val stateRepo: StateDataRepository

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
      submissionService.handleSubmission(registrationId, "testAuthProviderId", confirmationRefs)(hc, request, isAdmin = false)
        .map(_ => Ok)
  }

  def removeTaxRegistrationInformation(registrationId: String) = Action.async {
    implicit request =>
      cTMongoRepository.removeTaxRegistrationInformation(registrationId) map(if(_) Ok else BadRequest)
  }

  def updateTimePoint(timepoint: String) = Action.async {
    implicit request =>
      stateRepo.updateTimepoint(timepoint).map(tp => Ok(Json.toJson(tp)))
  }

  def pagerDuty(name: String) = Action.async {
    implicit request =>
      Logger.error(name)
      Future.successful(Ok)
  }
}
