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
import play.api.mvc.Action
import services.{MetricsService, RegistrationHoldingPenService}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

class ProcessIncorporationsControllerImp @Inject()(metricsService: MetricsService)
  extends ProcessIncorporationsController {
  override val regHoldingPenService = RegistrationHoldingPenService
}

trait ProcessIncorporationsController extends BaseController {

  val regHoldingPenService: RegistrationHoldingPenService

  def processIncorp = Action.async[JsValue](parse.json) {
    implicit request =>

      implicit val reads = IncorpStatus.reads
      withJsonBody[IncorpStatus]{ incorp =>
        regHoldingPenService.updateIncorp(incorp) map {
          if(_) Ok else BadRequest
        } recover {
          case e =>
            Logger.error("FAILED_DES_TOPUP")
            Logger.info(s"[ProcessIncorporationsController] [processIncorp] Topup failed for transaction ID: ${incorp.transactionId}")
            throw e
      }
    }
  }

  def processAdminIncorp = Action.async[JsValue](parse.json) {
    implicit request =>
      implicit val reads = IncorpStatus.reads
      withJsonBody[IncorpStatus]{ incorp =>
        regHoldingPenService.updateIncorp(incorp,true) map {
          if(_) Ok else BadRequest
      }
    }
  }

}
