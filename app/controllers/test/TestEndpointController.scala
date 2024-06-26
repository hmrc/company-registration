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

package controllers.test


import connectors.BusinessRegistrationConnector
import helpers.DateHelper
import models.ConfirmationReferences
import org.mongodb.scala.bson.BsonDocument
import utils.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories, ThrottleMongoRepository}
import services.SubmissionService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class TestEndpointControllerImpl @Inject()(val submissionService: SubmissionService,
                                           val bRConnector: BusinessRegistrationConnector,
                                           val repositories: Repositories,
                                           val controllerComponents: ControllerComponents
                                          )(implicit val ec: ExecutionContext) extends TestEndpointController {
  lazy val throttleMongoRepository: ThrottleMongoRepository = repositories.throttleRepository
  lazy val cTMongoRepository: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait TestEndpointController extends BackendBaseController with Logging {
  implicit val ec: ExecutionContext
  val throttleMongoRepository: ThrottleMongoRepository
  val cTMongoRepository: CorporationTaxRegistrationMongoRepository
  val bRConnector: BusinessRegistrationConnector
  val submissionService: SubmissionService

  def modifyThrottledUsers(usersIn: Int): Action[AnyContent] = Action.async {
    val date = DateHelper.getCurrentDay
    throttleMongoRepository.modifyThrottledUsers(date, usersIn).map(x => Ok(Json.parse(s"""{"users_in" : $x}""")))
  }

  private def dropCTCollection = {
    cTMongoRepository.collection.deleteMany(BsonDocument()).toFuture() map { _ => "CT collection was dropped" } recover {
      case _ => "A problem occurred and the CT Collection could not be dropped"
    }
  }

  def dropJourneyCollections: Action[AnyContent] = Action.async {
    implicit request =>
      for {
        cTDrop <- dropCTCollection
        bRDrop <- bRConnector.dropMetadataCollection
      } yield {
        Ok(Json.parse(s"""{"message":"$cTDrop $bRDrop"}"""))
      }
  }

  def updateConfirmationRefs(registrationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      val confirmationRefs = ConfirmationReferences("", "testOnlyTransactionId", Some("testOnlyPaymentRef"), Some("12"))
      submissionService.handleSubmission(registrationId, "testAuthProviderId", confirmationRefs, isAdmin = false)(hc, request)
        .map(_ => Ok)
  }

  def removeTaxRegistrationInformation(registrationId: String): Action[AnyContent] = Action.async {
    cTMongoRepository.removeTaxRegistrationInformation(registrationId) map (if (_) Ok else BadRequest)
  }

  def pagerDuty(name: String): Action[AnyContent] = Action.async {
    logger.error(name)
    Future.successful(Ok)
  }
}
