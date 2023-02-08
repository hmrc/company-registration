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

package controllers

import auth.AuthorisedActions
import models.Language
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import repositories.CorporationTaxRegistrationMongoRepository
import services.LanguageService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class LanguageController @Inject()(languageService: LanguageService,
                                   override val authConnector: AuthConnector,
                                   override val resource: CorporationTaxRegistrationMongoRepository,
                                   override val controllerComponents: ControllerComponents
                                  )(implicit val ec: ExecutionContext) extends BackendBaseController with AuthorisedActions {

  def updateLanguage(registrationId: String): Action[JsValue] = AuthorisedAction(registrationId).async(parse.json) { implicit request =>
    withJsonBody[Language] { language =>
      withRecovery("updateLanguage", registrationId) {
        languageService.updateLanguage(registrationId, language).map {
          case Some(_) => NoContent
          case None => NotFound
        }
      }
    }
  }

  def getLanguage(registrationId: String): Action[AnyContent] = AuthorisedAction(registrationId).async {
    withRecovery("getLanguage", registrationId) {
      languageService.retrieveLanguage(registrationId).map {
        case Some(email) => Ok(Json.toJson(email))
        case None => NotFound
      }
    }
  }

  private def withRecovery(functionName: String, registrationId: String)(f: => Future[Result]): Future[Result] = f recover {
    case e: Throwable =>
      val msg = s"An unexpected exception of type '${e.getClass.getSimpleName}' occurred for regId '$registrationId'"
      logger.error(s"[$functionName] $msg")
      InternalServerError(msg)
  }
}
