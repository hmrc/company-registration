/*
 * Copyright 2022 HM Revenue & Customs
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

import auth.AuthorisedActions
import javax.inject.{Inject, Singleton}
import models.{CompanyDetails, ElementsFromH02Reads, ErrorResponse, TradingDetails}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext


@Singleton
class CompanyDetailsController @Inject()(val metricsService: MetricsService,
                                         val companyDetailsService: CompanyDetailsService,
                                         val authConnector: AuthConnector,
                                         val repositories: Repositories,
                                         controllerComponents: ControllerComponents
                                        )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with AuthorisedActions {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository


  private[controllers] def mapToResponse(registrationID: String, res: CompanyDetails) = {
    Json.toJson(res).as[JsObject] ++
      Json.obj("tradingDetails" -> TradingDetails()) ++
      Json.obj(
        "links" -> Json.obj(
          "self" -> routes.CompanyDetailsController.retrieveCompanyDetails(registrationID).url,
          "registration" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url
        )
      )
  }

  def saveHandOff2ReferenceAndGenerateAckRef(registrationID: String): Action[JsValue] = AuthorisedAction(registrationID).async[JsValue](parse.json) { implicit request =>
    withJsonBody[String] { txId =>
      companyDetailsService.saveTxIdAndAckRef(registrationID, txId).map {
        ackRefJsObject => Ok(ackRefJsObject)
      }
    }(implicitly, implicitly, ElementsFromH02Reads.reads)
  }

  def retrieveCompanyDetails(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    val timer = metricsService.retrieveCompanyDetailsCRTimer.time()
    companyDetailsService.retrieveCompanyDetails(registrationID).map {
      case Some(details) => timer.stop()
        Ok(mapToResponse(registrationID, details))
      case None => timer.stop()
        NotFound(ErrorResponse.companyDetailsNotFound)
    }
  }

  def updateCompanyDetails(registrationID: String): Action[JsValue] = AuthorisedAction(registrationID).async[JsValue](parse.json) {
    implicit request =>
      val timer = metricsService.updateCompanyDetailsCRTimer.time()
      withJsonBody[CompanyDetails] {
        companyDetails =>
          companyDetailsService.updateCompanyDetails(registrationID, companyDetails).map {
            case Some(details) => timer.stop()
              Ok(mapToResponse(registrationID, details))
            case None => timer.stop()
              NotFound(ErrorResponse.companyDetailsNotFound)
          }
      }
  }
}