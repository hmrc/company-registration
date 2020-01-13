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
import models.{ContactDetails, ErrorResponse}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.{ContactDetailsService, MetricsService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

class ContactDetailsControllerImpl @Inject()(val metricsService: MetricsService,
                                             val contactDetailsService: ContactDetailsService,
                                             val authConnector: AuthConnector,
                                             val repositories: Repositories) extends ContactDetailsController {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait ContactDetailsController extends BaseController with AuthorisedActions {

  val contactDetailsService: ContactDetailsService
  val metricsService: MetricsService

  private[controllers] def mapToResponse(registrationID: String, res: ContactDetails)= {
    Json.toJson(res).as[JsObject] ++
      Json.obj(
        "links" -> Json.obj(
          "self" -> routes.ContactDetailsController.retrieveContactDetails(registrationID).url,
          "registration" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url
        )
      )
  }

  def retrieveContactDetails(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    implicit request =>
      val timer = metricsService.retrieveContactDetailsCRTimer.time()
      contactDetailsService.retrieveContactDetails(registrationID).map{
        case Some(details) => timer.stop()
          Ok(mapToResponse(registrationID, details))
        case None => timer.stop()
          NotFound(ErrorResponse.contactDetailsNotFound)
      }
  }

  def updateContactDetails(registrationID: String): Action[JsValue] = AuthorisedAction(registrationID).async(parse.json) {
    implicit request =>
      val timer = metricsService.updateContactDetailsCRTimer.time()
      withJsonBody[ContactDetails]{ contactDetails =>
        contactDetailsService.updateContactDetails(registrationID, contactDetails).map{
          case Some(details) => timer.stop()
            Ok(mapToResponse(registrationID, details))
          case None => timer.stop()
            NotFound(ErrorResponse.contactDetailsNotFound)
        }
      }
  }
}
