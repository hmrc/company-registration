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

package controllers

import auth._
import connectors.AuthConnector
import models.{ErrorResponse, TradingDetails}
import play.api.libs.json.{JsValue, Json}
import play.api.Logger
import uk.gov.hmrc.play.microservice.controller.BaseController
import services.{CorporationTaxRegistrationService, TradingDetailsService}
import play.api.mvc.Action

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TradingDetailsController extends TradingDetailsController {
  val tradingDetailsService = TradingDetailsService
  val resourceConn = CorporationTaxRegistrationService.corporationTaxRegistrationRepository
  val auth = AuthConnector
}

trait TradingDetailsController extends BaseController with Authenticated with Authorisation[String] {

  val tradingDetailsService : TradingDetailsService

  def retrieveTradingDetails(registrationID : String) = Action.async {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) => tradingDetailsService.retrieveTradingDetails(registrationID).map {
          case Some(res) => Ok(Json.toJson(res))
          case _ =>
            Logger.info(s"[TradingDetailsController] [retrieveTradingDetails] Authorised but no data for $registrationID")
            NotFound(ErrorResponse.tradingDetailsNotFound)
        }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[TradingDetailsController] [retrieveTradingDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[TradingDetailsController] [retrieveTradingDetails] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def updateTradingDetails(registrationID : String) : Action[JsValue] = Action.async[JsValue](parse.json) {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) =>
          withJsonBody[TradingDetails] {
            tradingDetails => tradingDetailsService.updateTradingDetails(registrationID, tradingDetails)
              .map{
                case Some(details) => Ok(Json.toJson(details))
                case None => NotFound(ErrorResponse.tradingDetailsNotFound)
              }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[TradingDetailsController] [retrieveTradingDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[TradingDetailsController] [retrieveTradingDetails] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }
}
