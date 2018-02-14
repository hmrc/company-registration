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
import models.{AcknowledgementReferences, ConfirmationReferences, CorporationTaxRegistrationRequest}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, AnyContentAsJson}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.{CorporationTaxRegistrationService, MetricsService}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import services.{CompanyRegistrationDoesNotExist, RegistrationProgressUpdated}

class CorporationTaxRegistrationControllerImpl @Inject()(val metricsService: MetricsService,
                                                         val authConnector: AuthClientConnector) extends CorporationTaxRegistrationController {
  val ctService: CorporationTaxRegistrationService = CorporationTaxRegistrationService
  val resource: CorporationTaxRegistrationMongoRepository = Repositories.cTRepository
}

trait CorporationTaxRegistrationController extends AuthorisedController {

  val ctService : CorporationTaxRegistrationService
  val metricsService : MetricsService

  def createCorporationTaxRegistration(registrationId: String): Action[JsValue] =
    AuthenticatedAction.retrieve(internalId).async(parse.json){ internalId =>
    implicit request =>
      val timer = metricsService.createCorporationTaxRegistrationCRTimer.time()
      withJsonBody[CorporationTaxRegistrationRequest] { ctRequest =>
        ctService.createCorporationTaxRegistrationRecord(internalId, registrationId, ctRequest.language).map{ res =>
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

  def retrieveCorporationTaxRegistration(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async{
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

  def retrieveFullCorporationTaxRegistration(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async{
    implicit request =>
      val timer = metricsService.retrieveFullCorporationTaxRegistrationCRTimer.time()
      ctService.retrieveCorporationTaxRegistrationRecord(registrationID).map {
        case Some(data) => timer.stop()
          Ok(Json.toJson(data))
        case _ => timer.stop()
          NotFound
      }
  }

  //HO5-1 and HO6
  def handleSubmission(registrationID : String): Action[JsValue] = AuthorisedAction(registrationID).async(parse.json){
    implicit request =>
      withJsonBody[ConfirmationReferences] { refs =>
        val timer = metricsService.updateReferencesCRTimer.time()
        ctService.handleSubmission(registrationID, refs)(hc, request.map(js => AnyContentAsJson(js))) map { references =>
          timer.stop()
          Logger.info(s"[Confirmation Refs] Acknowledgement ref:${references.acknowledgementReference} " +
            s"- Transaction id:${references.transactionId} - Payment ref:${references.paymentReference}")
          Ok(Json.toJson[ConfirmationReferences](references))
        }
      }
  }

  def retrieveConfirmationReference(registrationID: String): Action[AnyContent] = AuthorisedAction(registrationID).async{
    implicit request =>
      val timer = metricsService.retrieveConfirmationReferenceCRTimer.time()
      ctService.retrieveConfirmationReferences(registrationID) map {
        case Some(ref) => timer.stop()
          Ok(Json.toJson(ref))
        case None => timer.stop()
          NotFound
      }
  }

  def acknowledgementConfirmation(ackRef : String): Action[JsValue] = Action.async[JsValue](parse.json) {
    Logger.debug(s"[CorporationTaxRegistrationController] [acknowledgementConfirmation] confirming for ack ${ackRef}")
    implicit request =>
      withJsonBody[AcknowledgementReferences]{ ackRefsPayload =>
        val timer = metricsService.acknowledgementConfirmationCRTimer.time()
        ctService.updateCTRecordWithAckRefs(ackRef, ackRefsPayload) map {
          case Some(_) => timer.stop()
            Logger.debug(s"[CorporationTaxRegistrationController] - [acknowledgementConfirmation] : Updated Record")
            metricsService.ctutrConfirmationCounter.inc(1)
            Ok
          case None => timer.stop()
            NotFound("Ack ref not found")
        }
      }
  }

  def updateRegistrationProgress(registrationID: String): Action[JsValue] = AuthorisedAction(registrationID).async(parse.json) {
    implicit request =>
      withJsonBody[JsObject] { body =>
        val progress = (body \ "registration-progress").as[String]
        ctService.updateRegistrationProgress(registrationID, progress) map {
          case (RegistrationProgressUpdated) => Ok
          case (CompanyRegistrationDoesNotExist) => NotFound
        }
      }
  }
}
