/*
 * Copyright 2021 HM Revenue & Customs
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

import auth._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.ProcessIncorporationService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.JodaDateTimeFormatter

import scala.concurrent.ExecutionContext


@Singleton
class HeldController @Inject()(val authConnector: AuthConnector,
                               val service: ProcessIncorporationService,
                               val repositories: Repositories,
                               controllerComponents: ControllerComponents
                              )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with AuthorisedActions with JodaDateTimeFormatter {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository


  def fetchHeldSubmissionTime(regId: String): Action[AnyContent] = AuthenticatedAction.async {
    implicit request =>
      resource.getExistingRegistration(regId) map { doc =>
        doc.heldTimestamp.fold(NotFound(""))(date => Ok(Json.toJson(date)))
      }
  }

  def deleteSubmissionData(regId: String): Action[AnyContent] = AuthorisedAction(regId).async {
    implicit request =>
      service.deleteRejectedSubmissionData(regId).map {
        if (_) Ok else NotFound
      }
  }
}
