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
import models.{AcknowledgementReferences, ConfirmationReferences, CorporationTaxRegistrationRequest}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContentAsJson}
import repositories.Repositories
import services.{CorporationTaxRegistrationService, MetricsService}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class CorporationTaxRegistrationControllerImp @Inject() (metricsService: MetricsService) extends CorporationTaxRegistrationController {
  val ctService = CorporationTaxRegistrationService
  val resourceConn = Repositories.cTRepository
  val auth = AuthConnector
  val metrics = metricsService
}

trait CorporationTaxRegistrationController extends BaseController with Authenticated with Authorisation[String] {

  val ctService : CorporationTaxRegistrationService
  val metrics : MetricsService

  def createCorporationTaxRegistration(registrationId: String): Action[JsValue] = Action.async(parse.json) {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          val timer = metrics.createCorporationTaxRegistrationCRTimer.time()
          withJsonBody[CorporationTaxRegistrationRequest] {
            request => ctService.createCorporationTaxRegistrationRecord(context.ids.internalId, registrationId, request.language) map {
              res =>
                timer.stop()
                Created(
                  Json.obj(
                    "registrationID" -> res.registrationID,
                    "status" -> res.status,
                    "formCreationTimestamp" -> res.formCreationTimestamp,
                    "links" -> Json.obj(
                      "self" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationId).url
                    )
                  )
                )
            }
          }
        }
  }

  def retrieveCorporationTaxRegistration(registrationID: String) = Action.async {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) => val timer = metrics.retrieveCorporationTaxRegistrationCRTimer.time()
                              ctService.retrieveCorporationTaxRegistrationRecord(registrationID).map{
          case Some(data) => timer.stop()
            Ok(
            Json.obj(
              "registrationID" -> data.registrationID,
              "status" -> data.status,
              "formCreationTimestamp" -> data.formCreationTimestamp,
              "links" -> Json.obj(
                "self" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url
              )
            )
          )
          case _ => timer.stop()
            NotFound
        }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[CorporationTaxRegistrationController] [retrieveCTData] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[CorporationTaxRegistrationController] [retrieveCTData] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def retrieveFullCorporationTaxRegistration(registrationID: String) = Action.async {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) => val timer = metrics.retrieveFullCorporationTaxRegistrationCRTimer.time()
                              ctService.retrieveCorporationTaxRegistrationRecord(registrationID).map{
          case Some(data) => timer.stop()
                             Ok(Json.toJson(data))
          case _ => timer.stop()
            NotFound
        }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[CorporationTaxRegistrationController] [retrieveCTData] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[CorporationTaxRegistrationController] [retrieveCTData] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  //HO5-1 and HO6
  def handleSubmission(registrationID : String) = Action.async[JsValue](parse.json) {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) =>
          withJsonBody[ConfirmationReferences] {
            refs =>
              val timer = metrics.updateReferencesCRTimer.time()
              ctService.handleSubmission(registrationID, refs)(hc, request.map(js => AnyContentAsJson(js))) map {
                references =>
                  timer.stop()
                  Logger.info(s"[Confirmation Refs] Acknowledgement ref:${references.acknowledgementReference} " +
                    s"- Transaction id:${references.transactionId} - Payment ref:${references.paymentReference}")
                  Ok(Json.toJson[ConfirmationReferences](references))
              }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info("[CorporationTaxRegistrationController] [updateReferences] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[CorporationTaxRegistrationController] [updateReferences] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def retrieveConfirmationReference(registrationID: String) = Action.async {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) => val timer = metrics.retrieveConfirmationReferenceCRTimer.time()
                              ctService.retrieveConfirmationReferences(registrationID) map {
          case Some(ref) => timer.stop()
                            Ok(Json.toJson(ref))
          case None => timer.stop()
            NotFound
        }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[CorporationTaxRegistrationController] [retrieveConfirmationReference] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[CorporationTaxRegistrationController] [retrieveConfirmationReference] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def acknowledgementConfirmation(ackRef : String) = Action.async[JsValue](parse.json) {
    Logger.debug(s"[CorporationTaxRegistrationController] [acknowledgementConfirmation] confirming for ack ${ackRef}")
    implicit request =>
      withJsonBody[AcknowledgementReferences] {

        case accepted @ AcknowledgementReferences(Some(_), _, "04") =>
          val timer = metrics.acknowledgementConfirmationCRTimer.time()
          ctService.updateCTRecordWithAckRefs(ackRef, accepted) map {
            case Some(_) =>
              timer.stop()
              Logger.debug(s"[CorporationTaxRegistrationController] - [acknowledgementConfirmation] : Updated Record")
              metrics.ctutrConfirmationCounter.inc(1)
              Ok
            case None =>
              timer.stop()
              NotFound("Ack ref not found")
          }

        case AcknowledgementReferences(_, _, "04") =>
          Logger.error("CT_ACCEPTED_MISSING_UTR")
          Future.successful(BadRequest("Accepted but no CTUTR provided"))

        case rejected =>
          Logger.error("CT_REJECTED")
          ctService.rejectRegistration(ackRef) map {_ => Ok}
      }
  }

  def updateRegistrationProgress(registrationID: String) = Action.async[JsValue](parse.json) {
    implicit request =>
      authorised(registrationID){
        case Authorised(_) => {
          withJsonBody[JsObject] {
            body =>
              val progress = (body \ "registration-progress").as[String]
              ctService.updateRegistrationProgress(registrationID, progress) map { result =>
                import services.{CompanyRegistrationDoesNotExist, RegistrationProgressUpdated}
                result match {
                  case(RegistrationProgressUpdated) => Ok
                  case(CompanyRegistrationDoesNotExist) => NotFound
                }
              }
          }
        }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[CorporationTaxRegistrationController] [retrieveConfirmationReference] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
        Logger.info(s"[CorporationTaxRegistrationController] [retrieveConfirmationReference] User logged in but not authorised for resource $registrationID")
        Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

}
