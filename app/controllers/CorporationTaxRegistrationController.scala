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

import java.time.LocalTime

import javax.inject.Inject
import auth._
import models.{AcknowledgementReferences, ConfirmationReferences, CorporationTaxRegistrationRequest}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, AnyContentAsJson, Request}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.{CorporationTaxRegistrationService, MetricsService}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import services.{CompanyRegistrationDoesNotExist, RegistrationProgressUpdated}
import uk.gov.hmrc.auth.core.retrieve.Retrievals.credentials
import uk.gov.hmrc.play.microservice.controller.BaseController
import utils.{AlertLogging, Logging, PagerDutyKeys}

import scala.concurrent.Future

class CorporationTaxRegistrationControllerImpl @Inject()(
        val metricsService: MetricsService,
        val authConnector: AuthClientConnector,
        val ctService: CorporationTaxRegistrationService,
        val repositories: Repositories) extends CorporationTaxRegistrationController {

  val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait CorporationTaxRegistrationController extends BaseController with AuthorisedActions with Logging with AlertLogging{

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
  def handleSubmission(registrationID : String): Action[JsValue] =
    AuthorisedAction(registrationID).retrieve(credentials).async(parse.json){ credentials =>
    implicit request =>
      val requestAsAnyContentAsJson: Request[AnyContentAsJson] = request.map(AnyContentAsJson)
      withJsonBody[ConfirmationReferences] { refs =>
        val timer = metricsService.updateReferencesCRTimer.time()
        ctService.handleSubmission(registrationID, credentials.providerId, refs)(
          hc, requestAsAnyContentAsJson, isAdmin = false) map { references =>
          timer.stop()
          logger.info(s"[Confirmation Refs] Acknowledgement ref:${references.acknowledgementReference} " +
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
    logger.debug(s"[CorporationTaxRegistrationController] [acknowledgementConfirmation] confirming for ack ${ackRef}")
    implicit request =>
      withJsonBody[AcknowledgementReferences]{ etmpNotification =>

        (etmpNotification.ctUtr.isDefined, etmpNotification.status) match {

          case accepted @ (true, "04" | "05") => {
            val timer = metricsService.acknowledgementConfirmationCRTimer.time()
            ctService.updateCTRecordWithAckRefs(ackRef, etmpNotification) map {
              case Some(_) =>
                timer.stop()
                metricsService.ctutrConfirmationCounter.inc(1)
                Ok
              case None =>
                timer.stop()
                NotFound(s"Document not found for Ack ref: $ackRef")
            }
          }

          case rejected @ (_, "06" | "07" | "08" | "09" | "10") => {
            pagerduty(PagerDutyKeys.CT_REJECTED, Some(s"Received a Rejected response code (${etmpNotification.status}) from ETMP for ackRef: $ackRef"))
            ctService.updateCTRecordWithAckRefs(ackRef, etmpNotification.copy(ctUtr = None)) map { _ => Ok }
          }

          case missingCTUTR @ (_, "04" | "05") => {
            pagerduty(PagerDutyKeys.CT_ACCEPTED_MISSING_UTR, Some(s"Received an Accepted response code (${etmpNotification.status}) from ETMP without a CTUTR for ackRef: $ackRef"))
            Future.successful(BadRequest(s"Accepted but no CTUTR provided for ackRef: $ackRef"))
          }

          case unrecognised =>
            Future.failed(new RuntimeException(s"Unknown notification code (${etmpNotification.status}) received from ETMP for ackRef: $ackRef"))
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
