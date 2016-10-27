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

package controllers

import auth._
import connectors.AuthConnector
import models.{ConfirmationReferences, CorporationTaxRegistrationRequest}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Action
import services.CorporationTaxRegistrationService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CorporationTaxRegistrationController extends CorporationTaxRegistrationController {
  val ctService = CorporationTaxRegistrationService
  val resourceConn = CorporationTaxRegistrationService.CorporationTaxRegistrationRepository
  val auth = AuthConnector
}

trait CorporationTaxRegistrationController extends BaseController with Authenticated with Authorisation[String] {

  val ctService : CorporationTaxRegistrationService

  def createCorporationTaxRegistration(registrationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authenticated {
        case NotLoggedIn => Future.successful(Forbidden)
        case LoggedIn(context) =>
          withJsonBody[CorporationTaxRegistrationRequest] {
            request => ctService.createCorporationTaxRegistrationRecord(context.oid, registrationId, request.language) map {
              res =>
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
        case Authorised(_) => ctService.retrieveCorporationTaxRegistrationRecord(registrationID).map{
          case Some(data) => Ok(
            Json.obj(
              "registrationID" -> data.registrationID,
              "status" -> data.status,
              "formCreationTimestamp" -> data.formCreationTimestamp,
              "links" -> Json.obj(
                "self" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url
              )
            )
          )
          case _ => NotFound
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
        case Authorised(_) => ctService.retrieveCorporationTaxRegistrationRecord(registrationID).map{
          case Some(data) => Ok(Json.toJson(data))
          case _ => NotFound
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

  def updateReferences(registrationID : String) = Action.async[JsValue](parse.json) {
    implicit request =>
      println("Hello XXXXXXXXXXXXXXXXXX" + hc.headers)
      authorised(registrationID) {
        case Authorised(_) =>
          withJsonBody[ConfirmationReferences] {
            refs =>
              ctService.updateConfirmationReferences(registrationID, refs) map {
                case Some(references) => Ok(Json.toJson(references))
                case None => NotFound
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
        case Authorised(_) => ctService.retrieveConfirmationReference(registrationID) map {
          case Some(ref) => Ok(Json.toJson(ref))
          case None => NotFound
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
