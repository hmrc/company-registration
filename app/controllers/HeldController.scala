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

package controllers

import javax.inject.Inject

import auth._
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionMongoRepository, Repositories}
import services.RegistrationHoldingPenService
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class HeldControllerImpl @Inject()(
        val authConnector: AuthClientConnector,
        val service: RegistrationHoldingPenService,
        val repositories: Repositories
      ) extends HeldController {
  val heldRepo: HeldSubmissionMongoRepository = repositories.heldSubmissionRepository
  val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait HeldController extends BaseController with AuthorisedActions {

  val resource: CorporationTaxRegistrationMongoRepository
  val heldRepo: HeldSubmissionMongoRepository
  val service: RegistrationHoldingPenService

  def fetchHeldSubmissionTime(regId: String): Action[AnyContent] = AuthenticatedAction.async {
    implicit request =>
      resource.retrieveCorporationTaxRegistration(regId) flatMap { doc =>
        if (doc.exists(_.heldTimestamp.isDefined)) {
          Future.successful(Ok(Json.toJson(doc.get.heldTimestamp)))
        } else {
          heldRepo.retrieveHeldSubmissionTime(regId).map {
            case Some(time) => Ok(Json.toJson(time))
            case None => NotFound
          }
        }
      }
  }

  def deleteSubmissionData(regId: String): Action[AnyContent] = AuthorisedAction(regId).async {
    implicit request =>
      service.deleteRejectedSubmissionData(regId).map {
        if(_) Ok else NotFound
      }
  }
}
