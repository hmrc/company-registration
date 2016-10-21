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
import models.AccountingDetails
import models.ErrorResponse
import play.api.Logger
import play.api.libs.json.{Json, JsValue}
import play.api.mvc.Action
import services.{CorporationTaxRegistrationService, AccountingDetailsService}
import services.CorporationTaxRegistrationService
import uk.gov.hmrc.play.microservice.controller.BaseController
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future


object AccountingDetailsController extends AccountingDetailsController{
  override val auth = AuthConnector
  override val resourceConn = CorporationTaxRegistrationService.CorporationTaxRegistrationRepository
  override val accountingDetailsService = AccountingDetailsService
}

trait AccountingDetailsController extends BaseController with Authenticated with Authorisation[String] {

  val accountingDetailsService: AccountingDetailsService

  def retrieveAccountingDetails(registrationID: String) = Action.async {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) => accountingDetailsService.retrieveAccountingDetails(registrationID) map {
          case Some(res) => Ok(Json.toJson(res))
          case None => NotFound(ErrorResponse.accountingDetailsNotFound)
        }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[AccountingDetailsController] [retrieveAccountingDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[AccountingDetailsController] [retrieveAccountingDetails] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def updateAccountingDetails(registrationID: String) = Action.async[JsValue](parse.json) {
    implicit request =>
      authorised(registrationID){
        case Authorised(_) =>
          withJsonBody[AccountingDetails] {
            companyDetails => accountingDetailsService.updateAccountingDetails(registrationID, companyDetails)
              .map{
                case Some(details) => Ok(Json.toJson(details))
                case None => NotFound(ErrorResponse.companyDetailsNotFound)
              }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[AccountingDetailsController] [updateAccountingDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[AccountingDetailsController] [updateAccountingDetails] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }
}
