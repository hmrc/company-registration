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

package controllers.test

import auth.AuthorisedActions
import javax.inject.Inject
import models.Email
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.EmailService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

class EmailControllerImpl @Inject()(val emailService: EmailService,
                                    val authConnector: AuthConnector,
                                    repositories: Repositories) extends EmailController {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait EmailController extends BaseController with AuthorisedActions {

  val emailService: EmailService

  def updateEmail(registrationId: String): Action[JsValue] = AuthorisedAction(registrationId).async(parse.json){
    implicit request =>
      withJsonBody[Email]{
        emailService.updateEmail(registrationId, _).map{
          case Some(email) => Ok(Json.toJson(email))
          case None        => NotFound
        }
      }
  }

  def retrieveEmail(registrationId: String): Action[AnyContent] = AuthorisedAction(registrationId).async{
    implicit request =>
      emailService.retrieveEmail(registrationId).map{
        case Some(email) => Ok(Json.toJson(email))
        case None        => NotFound
      }
  }
}
