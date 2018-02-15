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
import play.api.mvc.AnyContent
import repositories.CorporationTaxRegistrationMongoRepository
import models.{ErrorResponse, TradingDetails}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Action
import repositories.Repositories
import services.{MetricsService, TradingDetailsService}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

class TradingDetailsControllerImpl @Inject()(val metricsService: MetricsService,
                                             val tradingDetailsService: TradingDetailsService,
                                             val authConnector: AuthClientConnector) extends TradingDetailsController {
  val resource: CorporationTaxRegistrationMongoRepository = Repositories.cTRepository
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
