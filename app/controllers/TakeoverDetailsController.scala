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

import auth.AuthorisedActions
import javax.inject.{Inject, Singleton}
import models.TakeoverDetails
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.TakeoverDetailsService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class TakeoverDetailsController @Inject()(val repositories: Repositories,
                                          val takeoverDetailsService: TakeoverDetailsService,
                                          val authConnector: AuthConnector,
                                          controllerComponents: ControllerComponents)
                                         (implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with AuthorisedActions {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository

  def getBlock(registrationID: String): Action[AnyContent] =
    AuthorisedAction(registrationID).async {
      implicit request =>
        takeoverDetailsService.retrieveTakeoverDetailsBlock(registrationID).map {
          case Some(takeoverDetails) =>
            Ok(Json.toJson(takeoverDetails)(TakeoverDetails.format))
          case None =>
            NoContent
        }
    }

  def saveBlock(registrationID: String): Action[JsValue] =
    AuthorisedAction(registrationID).async[JsValue](parse.json) {
      implicit request =>
        withJsonBody[TakeoverDetails] {
          takeoverDetails =>
            takeoverDetailsService.updateTakeoverDetailsBlock(registrationID, takeoverDetails).map {
              takeoverDetails => Ok(Json.toJson(takeoverDetails)(TakeoverDetails.format))
            }
        }
    }
}