/*
 * Copyright 2017 HM Revenue & Customs
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
import connectors.AuthConnector
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionMongoRepository, Repositories}
import services.RegistrationHoldingPenService
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

object HeldController extends HeldController {
  val auth = AuthConnector
  val resourceConn = Repositories.cTRepository
  val heldRepo = Repositories.heldSubmissionRepository
  val service = RegistrationHoldingPenService
}

trait HeldController extends BaseController with Authenticated with Authorisation[String] {

  val resourceConn: CorporationTaxRegistrationMongoRepository
  val heldRepo: HeldSubmissionMongoRepository
  val service: RegistrationHoldingPenService

  def fetchHeldSubmissionTime(regId: String) = Action.async {
    implicit request =>
      authenticated {
        case LoggedIn(_) =>
            resourceConn.retrieveCorporationTaxRegistration(regId) flatMap { doc =>
              if(doc.exists(_.heldTimestamp.isDefined)) {
                Future.successful(Ok(Json.toJson(doc.get.heldTimestamp)))
              } else {
                heldRepo.retrieveHeldSubmissionTime(regId).map {
                  case Some(time) => Ok(Json.toJson(time))
                  case None => NotFound
                }
              }
            }
        case _ =>
          Logger.info(s"[HeldController] [fetchHeldSubmissionTime] User not logged in")
          Future.successful(Forbidden)
      }
  }

  def deleteSubmissionData(regId: String) = Action.async {
    implicit request =>
      authorised(regId) {
        case Authorised(_) => service.deleteRejectedSubmissionData(regId).map {
          case true => Ok
          case false => NotFound
        }
        case AuthResourceNotFound(_) =>
          Logger.info(s"[HeldController] [deleteHeldSubmissionData] User registration not found")
          Future.successful(NotFound)
        case _ =>
          Logger.info(s"[HeldController] [deleteHeldSubmissionData] User not logged in")
          Future.successful(Forbidden)
      }
  }

}

