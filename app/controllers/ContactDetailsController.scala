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
import connectors.AuthConnector
import models.{ContactDetails, ErrorResponse}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Action
import repositories.Repositories
import services.{ContactDetailsService, MetricsService}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class ContactDetailsControllerImp @Inject() (metrics: MetricsService,
                                             contactDetailsServ: ContactDetailsService) extends ContactDetailsController{
  override val auth = AuthConnector
  override val resourceConn = Repositories.cTRepository
  override val contactDetailsService = contactDetailsServ
  override val metricsService: MetricsService = metrics
}

trait ContactDetailsController extends BaseController with Authenticated with Authorisation[String] {

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

  def retrieveContactDetails(registrationID: String) = Action.async {
    implicit request =>
      authorised(registrationID){
        case Authorised(_) => val timer = metricsService.retrieveContactDetailsCRTimer.time()
                              contactDetailsService.retrieveContactDetails(registrationID) map {
          case Some(details) => timer.stop()
                                Ok(mapToResponse(registrationID, details))
          case None => timer.stop()
            NotFound(ErrorResponse.contactDetailsNotFound)
        }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[ContactDetailsController] [retrieveContactDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[ContactDetailsController] [retrieveContactDetails] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def updateContactDetails(registrationID: String) = Action.async[JsValue](parse.json) {
    implicit request =>
      authorised(registrationID){
        case Authorised(_) => val timer = metricsService.updateContactDetailsCRTimer.time()
          withJsonBody[ContactDetails]{ contactDetails =>
            contactDetailsService.updateContactDetails(registrationID, contactDetails) map {
              case Some(details) => timer.stop()
                                    Ok(mapToResponse(registrationID, details))
              case None => timer.stop()
                NotFound(ErrorResponse.contactDetailsNotFound)
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[ContactDetailsController] [retrieveContactDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[ContactDetailsController] [retrieveContactDetails] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Logger.info(s"[ContactDetailsController] [retrieveContactDetails] Auth resource not found $registrationID")
          Future.successful(NotFound)
      }
  }
}
