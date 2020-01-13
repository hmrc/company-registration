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

import auth.{AuthorisedActions, CryptoSCRS}
import javax.inject.Inject
import models.validation.APIValidation
import models.{AcknowledgementReferences, ConfirmationReferences}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContentAsJson, Request}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.{MetricsService, SubmissionService}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals.credentials
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import utils.{AlertLogging, Logging, PagerDutyKeys}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SubmissionControllerImpl @Inject()(val metricsService: MetricsService,
                                         val authConnector: AuthConnector,
                                         val submissionService: SubmissionService,
                                         val repositories: Repositories,
                                         val alertLogging: AlertLogging,
                                         val cryptoSCRS: CryptoSCRS) extends SubmissionController {
  lazy val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}


trait SubmissionController extends BaseController with AuthorisedActions with Logging {

  val metricsService: MetricsService
  val submissionService: SubmissionService
  val alertLogging: AlertLogging
  val cryptoSCRS: CryptoSCRS

  def handleUserSubmission(registrationID : String): Action[JsValue] =
    AuthorisedAction(registrationID).retrieve(credentials).async(parse.json){ credentials =>
      implicit request =>
        val requestAsAnyContentAsJson: Request[AnyContentAsJson] = request.map(AnyContentAsJson)
        withJsonBody[ConfirmationReferences] { refs =>
          val timer = metricsService.updateReferencesCRTimer.time()
          submissionService.handleSubmission(registrationID, credentials.providerId, refs, isAdmin = false)(
            hc, requestAsAnyContentAsJson) map { references =>
            timer.stop()
            logger.info(s"[Confirmation Refs] Acknowledgement ref:${references.acknowledgementReference} " +
              s"- Transaction id:${references.transactionId} - Payment ref:${references.paymentReference}")
            Ok(Json.toJson[ConfirmationReferences](references))
          }
        }
    }

  def acknowledgementConfirmation(ackRef : String): Action[JsValue] = Action.async[JsValue](parse.json) {
    logger.debug(s"[CorporationTaxRegistrationController] [acknowledgementConfirmation] confirming for ack $ackRef")
    implicit request =>
      withJsonBody[AcknowledgementReferences]{ etmpNotification =>

        (etmpNotification.ctUtr.isDefined, etmpNotification.status) match {
          case accepted @ (true, "04" | "05") =>
            val timer = metricsService.acknowledgementConfirmationCRTimer.time()
            submissionService.updateCTRecordWithAckRefs(ackRef, etmpNotification) map {
              case Some(_) =>
                timer.stop()
                metricsService.ctutrConfirmationCounter.inc(1)
                Ok
              case None =>
                timer.stop()
                NotFound(s"Document not found for Ack ref: $ackRef")
            }
          case rejected @ (_, "06" | "07" | "08" | "09" | "10") =>
            alertLogging.pagerduty(PagerDutyKeys.CT_REJECTED, Some(s"Received a Rejected response code (${etmpNotification.status}) from ETMP for ackRef: $ackRef"))
            submissionService.updateCTRecordWithAckRefs(ackRef, etmpNotification.copy(ctUtr = None)) map { _ => Ok }
          case missingCTUTR @ (_, "04" | "05") =>
            alertLogging.pagerduty(PagerDutyKeys.CT_ACCEPTED_MISSING_UTR, Some(s"Received an Accepted response code (${etmpNotification.status}) from ETMP without a CTUTR for ackRef: $ackRef"))
            Future.successful(BadRequest(s"Accepted but no CTUTR provided for ackRef: $ackRef"))
          case unrecognised =>
            Future.failed(new RuntimeException(s"Unknown notification code (${etmpNotification.status}) received from ETMP for ackRef: $ackRef"))
        }
      }(implicitly,implicitly,AcknowledgementReferences.format(APIValidation,cryptoSCRS))
  }
}
