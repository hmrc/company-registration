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
import services.{MetricsService, UserAccessService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserAccessController @Inject()(val authConnector: AuthConnector,
                                     val metricsService: MetricsService,
                                     val userAccessService: UserAccessService,
                                     controllerComponents: ControllerComponents
                                    )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with AuthenticatedActions {

  def checkUserAccess: Action[AnyContent] = AuthenticatedAction.retrieve(internalId).async { intId =>
    implicit request =>
      val timer = metricsService.userAccessCRTimer.time()
      userAccessService.checkUserAccess(intId) flatMap {
        case Right(res) => {
          timer.stop()
          Future.successful(Ok(Json.toJson(res)))
        }
        case Left(_) => timer.stop()
          Future.successful(TooManyRequests)
      }
  }
}
