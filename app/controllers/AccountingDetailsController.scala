/*
 * Copyright 2024 HM Revenue & Customs
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
import models.{AccountingDetails, ErrorResponse}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.{AccountingDetailsService, MetricsService, PrepareAccountService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext


@Singleton
class AccountingDetailsController @Inject()(val metricsService: MetricsService,
                                            val prepareAccountService: PrepareAccountService,
                                            val accountingDetailsService: AccountingDetailsService,
                                            val authConnector: AuthConnector,
                                            val repositories: Repositories,
                                            controllerComponents: ControllerComponents
                                           )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with AuthorisedActions {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository


  private def mapToResponse(registrationID: String, res: AccountingDetails) = {
    Json.toJson(res).as[JsObject] ++
      Json.obj(
        "links" -> Json.obj(
          "self" -> routes.AccountingDetailsController.retrieveAccountingDetails(registrationID).url,
          "registration" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url
        )
      )
  }

  def retrieveAccountingDetails(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    val timer = metricsService.retrieveAccountingDetailsCRTimer.time()
    accountingDetailsService.retrieveAccountingDetails(registrationID) map {
      case Some(details) => timer.stop()
        Ok(mapToResponse(registrationID, details))
      case None => timer.stop()
        NotFound(ErrorResponse.accountingDetailsNotFound)
    }
  }

  def updateAccountingDetails(registrationID: String): Action[JsValue] = AuthorisedAction(registrationID).async[JsValue](parse.json) {
    implicit request =>
      val timer = metricsService.updateAccountingDetailsCRTimer.time()
      withJsonBody[AccountingDetails] { companyDetails =>
        for {
          accountingDetails <- accountingDetailsService.updateAccountingDetails(registrationID, companyDetails)
          _ <- prepareAccountService.updateEndDate(registrationID)
        } yield {
          accountingDetails match {
            case Some(details) => timer.stop()
              Ok(mapToResponse(registrationID, details))
            case None => timer.stop()
              NotFound(ErrorResponse.companyDetailsNotFound)
          }
        }
      }
  }
}
