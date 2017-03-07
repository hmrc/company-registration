/*
 * Copyright 2017 HM Revenue & Customs
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
import models.{AccountingDetails, CompanyDetails, ErrorResponse}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Action
import services.{AccountingDetailsService, CorporationTaxRegistrationService, MetricsService, PrepareAccountService}
import uk.gov.hmrc.play.microservice.controller.BaseController
import javax.inject.Inject

import repositories.Repositories

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class AccountingDetailsControllerImp @Inject() (metrics: MetricsService,
                                                prepareAccountServ: PrepareAccountService)
  extends AccountingDetailsController {
  override val auth = AuthConnector
  override val resourceConn = Repositories.cTRepository
  override val accountingDetailsService = AccountingDetailsService
  override val metricsService: MetricsService = metrics
  override val prepareAccountService = prepareAccountServ
}

trait AccountingDetailsController extends BaseController with Authenticated with Authorisation[String] {

  val accountingDetailsService: AccountingDetailsService
  val metricsService: MetricsService
  val prepareAccountService: PrepareAccountService


  private def mapToResponse(registrationID: String, res: AccountingDetails)= {
    Json.toJson(res).as[JsObject] ++
      Json.obj(
        "links" -> Json.obj(
          "self" -> routes.AccountingDetailsController.retrieveAccountingDetails(registrationID).url,
          "registration" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url
        )
      )
  }

  def retrieveAccountingDetails(registrationID: String) = Action.async {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) => val timer = metricsService.retrieveAccountingDetailsCRTimer.time()
                              accountingDetailsService.retrieveAccountingDetails(registrationID) map {
          case Some(details) => timer.stop()
                                Ok(mapToResponse(registrationID, details))
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
          val timer = metricsService.updateAccountingDetailsCRTimer.time()
          withJsonBody[AccountingDetails] {
            companyDetails =>
              for {
                accountingDetails <- accountingDetailsService.updateAccountingDetails(registrationID, companyDetails)
                _ <- prepareAccountService.updateEndDate(registrationID)
              } yield {
                accountingDetails match {
                  case Some(details) => timer.stop()
                    Ok(mapToResponse(registrationID, details))
                  case None => NotFound(ErrorResponse.companyDetailsNotFound)
                }
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
