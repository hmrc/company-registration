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

import models._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContentAsJson, Request}
import services._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class ProcessIncorporationsControllerImpl @Inject()(
        metricsService: MetricsService,
        val regHoldingPenService: RegistrationHoldingPenService,
        val corpTaxRegService: CorporationTaxRegistrationService
      ) extends ProcessIncorporationsController

trait ProcessIncorporationsController extends BaseController {

  val regHoldingPenService: RegistrationHoldingPenService
  val corpTaxRegService: CorporationTaxRegistrationService

  private def logFailedTopup(txId: String) = {
    Logger.error("FAILED_DES_TOPUP")
    Logger.info(s"FAILED_DES_TOPUP - Topup failed for transaction ID: $txId")
  }

  def processIncorp: Action[JsValue] = Action.async[JsValue](parse.json) {
    implicit request =>

      implicit val reads = IncorpStatus.reads
      withJsonBody[IncorpStatus]{ incorp =>
        val requestAsAnyContentAsJson: Request[AnyContentAsJson] = request.map(AnyContentAsJson)
        regHoldingPenService.processIncorporationUpdate(incorp.toIncorpUpdate) flatMap {
          if(_) Future.successful(Ok) else {
            corpTaxRegService.setupPartialForTopupOnLocked(incorp.transactionId)(hc, requestAsAnyContentAsJson, isAdmin = false) map { _ =>
              Logger.info(s"[processIncorp] Sent partial submission in response to locked document on incorp update: ${incorp.transactionId}")
              Accepted
            }
          }
        } recover {
          case NoSessionIdentifiersInDocument => Ok
          case _: MissingRegDocument => Ok
          case e =>
            logFailedTopup(incorp.transactionId)
            throw e
      }
    }
  }

  def processAdminIncorp: Action[JsValue] = Action.async[JsValue](parse.json) {
    implicit request =>
      implicit val reads = IncorpStatus.reads
      withJsonBody[IncorpStatus]{ incorp =>
        regHoldingPenService.processIncorporationUpdate(incorp.toIncorpUpdate, isAdmin = true) map {
          if(_) { Ok } else {
            logFailedTopup(incorp.transactionId)
            BadRequest
          }
      } recover {
          case e =>
            logFailedTopup(incorp.transactionId)
            throw e
        }
    }
  }

}
