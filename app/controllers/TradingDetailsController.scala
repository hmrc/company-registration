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

package controllers

import auth._
import javax.inject.Inject
import models.{ErrorResponse, TradingDetails}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.{MetricsService, TradingDetailsService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

class TradingDetailsControllerImpl @Inject()(val metricsService: MetricsService,
                                             val tradingDetailsService: TradingDetailsService,
                                             val authConnector: AuthConnector,
                                             val repositories: Repositories) extends TradingDetailsController {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}


trait TradingDetailsController extends BaseController with AuthorisedActions {

  val tradingDetailsService : TradingDetailsService
  val metricsService: MetricsService

  def retrieveTradingDetails(registrationID : String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    implicit request =>
      val timer = metricsService.retrieveTradingDetailsCRTimer.time()
      tradingDetailsService.retrieveTradingDetails(registrationID).map {
          case Some(res) => timer.stop()
            Ok(Json.toJson(res))
          case _ => timer.stop()
            NotFound(ErrorResponse.tradingDetailsNotFound)
        }
  }

  def updateTradingDetails(registrationID : String) : Action[JsValue] = AuthorisedAction(registrationID).async(parse.json) {
    implicit request =>
          val timer = metricsService.updateTradingDetailsCRTimer.time()
          withJsonBody[TradingDetails] {
            tradingDetails => tradingDetailsService.updateTradingDetails(registrationID, tradingDetails)
              .map{
                case Some(details) => timer.stop()
                                      Ok(Json.toJson(details))
                case None => timer.stop()
                  NotFound(ErrorResponse.tradingDetailsNotFound)
              }
          }
  }
}
