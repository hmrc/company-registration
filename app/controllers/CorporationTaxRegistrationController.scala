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
import models._
import models.validation.APIValidation
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.{CorporationTaxRegistrationService, MetricsService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import utils.{AlertLogging, Logging}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CorporationTaxRegistrationControllerImpl @Inject()(
                                                          val metricsService: MetricsService,
                                                          val authConnector: AuthConnector,
                                                          val ctService: CorporationTaxRegistrationService,
                                                          val repositories: Repositories,
                                                          val alertLogging: AlertLogging,
                                                          val cryptoSCRS: CryptoSCRS) extends CorporationTaxRegistrationController {

  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait CorporationTaxRegistrationController extends BaseController with AuthorisedActions with Logging {

  val ctService: CorporationTaxRegistrationService
  val metricsService: MetricsService
  val cryptoSCRS: CryptoSCRS

  def createCorporationTaxRegistration(registrationId: String): Action[JsValue] =
    AuthenticatedAction.retrieve(internalId).async(parse.json) { internalId =>
      implicit request =>
        val timer = metricsService.createCorporationTaxRegistrationCRTimer.time()
        withJsonBody[CorporationTaxRegistrationRequest] { ctRequest =>
          ctService.createCorporationTaxRegistrationRecord(internalId, registrationId, ctRequest.language).map { res =>
            timer.stop()
            Created(Json.obj(
              "registrationID" -> res.registrationID,
              "status" -> res.status,
              "formCreationTimestamp" -> res.formCreationTimestamp,
              "links" -> Json.obj(
                "self" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationId).url
              )
            ))
          }
        }
    }

  def retrieveCorporationTaxRegistration(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    implicit request =>
      val timer = metricsService.retrieveCorporationTaxRegistrationCRTimer.time()
      ctService.retrieveCorporationTaxRegistrationRecord(registrationID).map {
        case Some(data) => timer.stop()
          Ok(Json.obj(
            "registrationID" -> data.registrationID,
            "status" -> data.status,
            "formCreationTimestamp" -> data.formCreationTimestamp,
            "links" -> Json.obj(
              "self" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url
            ))
          )
        case _ => timer.stop()
          NotFound
      }
  }

  def retrieveFullCorporationTaxRegistration(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    implicit request =>
      val timer = metricsService.retrieveFullCorporationTaxRegistrationCRTimer.time()
      ctService.retrieveCorporationTaxRegistrationRecord(registrationID).map {
        case Some(data) => timer.stop()
          Ok(Json.toJson(data)(CorporationTaxRegistration.format(APIValidation,cryptoSCRS)))
        case _ => timer.stop()
          NotFound
      }
  }

  def retrieveConfirmationReference(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async {
    implicit request =>
      val timer = metricsService.retrieveConfirmationReferenceCRTimer.time()
      ctService.retrieveConfirmationReferences(registrationID) map {
        case Some(ref) => timer.stop()
          Ok(Json.toJson(ref))
        case None => timer.stop()
          NotFound
      }
  }

  def updateRegistrationProgress(registrationID: String): Action[JsValue] = AuthorisedAction(registrationID).async(parse.json) {
    implicit request =>
      withJsonBody[JsObject] { body =>
        val progress = (body \ "registration-progress").as[String]
        ctService.updateRegistrationProgress(registrationID, progress) map {
          case Some(_) => Ok
          case _ => NotFound
        }
      }
  }

  def roAddressValid(): Action[JsValue] = Action.async[JsValue](parse.json) {
    implicit request =>
      withJsonBody[CHROAddress] { body =>

        ctService.convertROToPPOBAddress(body) match {
          case Some(ppob) => {
            Future.successful(Ok(Json.toJson(ppob)(PPOBAddress.writes)))
          }
          case _ => Future.successful(BadRequest)
        }
      }
  }
}
