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

import auth.{LoggedIn, NotLoggedIn, Authenticated}
import connectors.AuthConnector
import models.{ErrorResponse, CompanyDetails}
import play.api.libs.json.{Json, JsValue}
import play.api.mvc.Action
import services.CompanyDetailsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CompanyDetailsController extends CompanyDetailsController {
  override val auth: AuthConnector = AuthConnector
  override val companyDetailsService = CompanyDetailsService
}

trait CompanyDetailsController extends BaseController with Authenticated {

  val companyDetailsService: CompanyDetailsService

  def retrieveCompanyDetails(registrationID: String) = Action.async {
    implicit request =>
      authenticated{
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          companyDetailsService.retrieveCompanyDetails(registrationID).map{
            case Some(res) => Ok(Json.toJson(res))
            case _ => NotFound(ErrorResponse.companyDetailsNotFound)
          }
      }
  }

  def updateCompanyDetails(registrationID: String): Action[JsValue] = Action.async[JsValue](parse.json) {
    implicit request =>
      authenticated{
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          withJsonBody[CompanyDetails] {
            companyDetails => companyDetailsService.updateCompanyDetails(registrationID, companyDetails)
              .map{
                case Some(details) => Ok(Json.toJson(details))
                case None => NotFound(ErrorResponse.companyDetailsNotFound)
              }
          }
      }
  }
}
