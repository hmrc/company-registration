/*
 * Copyright 2016 HM Revenue & Customs
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

import auth.Authorisation
import connectors.AuthConnector
import models.Email
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.Repositories
import services.EmailService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object EmailController extends EmailController {
  val emailService = EmailService
  val resourceConn = Repositories.cTRepository
  val auth = AuthConnector
}

trait EmailController extends BaseController with Authorisation[String]{
  val emailService: EmailService

  def updateEmail(registrationId: String) = Action.async(parse.json) {
    implicit request =>
      authorisedFor(registrationId){ _ =>
        withJsonBody[Email]{
          emailService.updateEmail(registrationId, _).map {
            case Some(email) => Ok(Json.toJson(email))
            case None => NotFound
          }
        }
      }
  }

  def retrieveEmail(registrationId: String) = Action.async {
    implicit request =>
      authorisedFor(registrationId){ _ =>
        emailService.retrieveEmail(registrationId).map{
          case Some(email) => Ok(Json.toJson(email))
          case None => NotFound
        }
      }
  }
}