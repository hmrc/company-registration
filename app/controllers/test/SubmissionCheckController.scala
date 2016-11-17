/*
 * Copyright 2016 HM Revenue & Customs
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

package controllers.test

import play.api.libs.json.JsObject
import play.api.mvc.Action
import services.RegistrationHoldingPenService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object SubmissionCheckController extends SubmissionCheckController {
  val service = RegistrationHoldingPenService
}

trait SubmissionCheckController extends BaseController {

  val service : RegistrationHoldingPenService

  def triggerSubmissionCheck = Action.async {
    implicit request =>
      service.updateNextSubmissionByTimepoint map {
        res => Ok(res)
      }
  }
}
