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
import models.PrepareAccountModel
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Action
import services.{CorporationTaxRegistrationService, PrepareAccountService}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PrepareAccountController extends PrepareAccountController {
  override val auth = AuthConnector
  override val resourceConn = CorporationTaxRegistrationService.corporationTaxRegistrationRepository
  override val service = PrepareAccountService
}

trait PrepareAccountController extends BaseController with Authenticated with Authorisation[String] {

  val service: PrepareAccountService

 def updateCompanyEndDate(registrationID: String) = Action.async[JsValue](parse.json) {
   implicit request =>
     authorised(registrationID){
       case Authorised(_) =>
         withJsonBody[PrepareAccountModel]{ model =>
          service.updateEndDate(registrationID, model).map{
            case Some(res) => Ok(Json.toJson(res))
            case None =>
              Logger.error(s"[PrepareAccountController] [updateCompanyEndDate] Company preparation date for user: $registrationID not found")
              NotFound
          }
         }
       case NotLoggedInOrAuthorised =>
         Logger.info(s"[PrepareAccountController] [updateCompanyEndDate] User not logged in")
         Future.successful(Forbidden)
       case NotAuthorised(_) =>
         Logger.info(s"[PrepareAccountController] [updateCompanyEndDate] User logged in but not authorised for resource $registrationID")
         Future.successful(Forbidden)
       case AuthResourceNotFound(_) => Logger.info(s"[PrepareAccountController] [updateCompanyEndDate] Auth resource not found $registrationID")
         Future.successful(NotFound)
     }
 }
}
