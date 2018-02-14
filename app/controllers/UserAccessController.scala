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
import connectors.AuthConnector
import play.api.libs.json.Json
import services.{MetricsService, UserAccessService}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future

class UserAccessControllerImp @Inject() (val authConnector: AuthClientConnector,
                                         val metricsService: MetricsService,
                                         val userAccessService: UserAccessService) extends UserAccessController

trait UserAccessController extends AuthenticatedController {
  val userAccessService: UserAccessService
  val metricsService: MetricsService

  def checkUserAccess = AuthenticatedAction.retrieve(internalId).async { intId =>
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
