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

package controllers.admin

import javax.inject.{Inject, Singleton}

import models.{ConfirmationReferences, HO6RegistrationInformation}
import models.admin.{Admin, HO6Identifiers, HO6Response}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc._
import services.CorporationTaxRegistrationService
import services.admin.AdminService
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.microservice.controller.BaseController
import cats.instances.FutureInstances
import cats.syntax.{ApplicativeSyntax, FlatMapSyntax}
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class AdminControllerImpl @Inject()(val adminService: AdminService) extends AdminController {
  lazy val ctService = CorporationTaxRegistrationService
}

trait AdminController extends BaseController with FutureInstances with ApplicativeSyntax with FlatMapSyntax {

  val adminService: AdminService
  val ctService : CorporationTaxRegistrationService

  def fetchHO6RegistrationInformation(regId: String): Action[AnyContent] = Action.async {
    implicit request =>
      adminService.fetchHO6RegistrationInformation(regId) map {
        case Some(ho6RegInfo) => Ok(Json.toJson(ho6RegInfo)(HO6RegistrationInformation.writes))
        case None => NotFound
      }
  }

  def migrateHeldSubmissions: Action[AnyContent] = Action.async {
    implicit request =>
      adminService.migrateHeldSubmissions map { migrationList =>
        Ok(migrationJsonResponse(migrationList))
      }
  }

  def updateConfirmationReferences(): Action[JsValue] = Action.async(BodyParsers.parse.json) {
    implicit request =>
      implicit val format = HO6Identifiers.format
      withJsonBody[HO6Identifiers] { identifiers =>
        val strideUser = identifiers.strideUser
        val confirmationReferences = buildConfirmationRefs(identifiers)
        val updatedHc = updateHeaderCarrierWithSessionId(identifiers.sessionId)
        val admin = Admin(identifiers.credId)
        fetchStatus(identifiers.registrationId) { statusBefore =>
          ctService.handleSubmission(identifiers.registrationId, confirmationReferences, Some(admin))(updatedHc, request.map(js => AnyContentAsJson(js))) flatMap {
            references =>
              Logger.info(s"[Admin Confirmation Refs] Acknowledgement ref : ${references.acknowledgementReference} " +
                s"- Transaction id : ${references.transactionId} - Payment ref : ${references.paymentReference}")
              buildResponse(strideUser, isSuccess = true, statusBefore, identifiers)
          } recoverWith {
            case _: CorporationTaxRegistrationService#FailedToGetCTData =>
              Logger.error(s"[Admin] [updateConfirmationReferences] No CT data found for regId : ${identifiers.registrationId}")
              buildResponse(strideUser, isSuccess = false, statusBefore, identifiers)
            case ex: Exception =>
              Logger.error(s"[Admin] [updateConfirmationReferences] Exception thrown for regId : ${identifiers.registrationId}", ex)
              buildResponse(strideUser, isSuccess = false, statusBefore, identifiers)
          }
        }
      }
  }

  private def buildResponse(strideUser: String, isSuccess: Boolean, statusBefore: String, identifiers: HO6Identifiers)(implicit hc: HeaderCarrier): Future[Result] = {
    fetchStatus(identifiers.registrationId) { statusAfter =>
      val response = HO6Response(success = isSuccess, statusBefore, statusAfter)
      val jsonResponse = Json.toJson[HO6Response](response)(HO6Response.format)
      val result = (if(isSuccess) Ok(jsonResponse) else NotFound(jsonResponse)).pure[Future]
      auditAdminEvent(strideUser, identifiers, response) flatMap {
        case AuditResult.Success => result
        case AuditResult.Failure(errMsg, _) =>
          Logger.error(s"[Admin] [Audit] - Failed to audit HO6 admin release event for regId : ${identifiers.registrationId} - reason - $errMsg")
          result
      }
    }
  }

  private def migrationJsonResponse(migrations: List[Boolean]): JsObject = {
    Json.obj(
      "total-attempted-migrations" -> migrations.size,
      "total-success" -> migrations.count(_ == true)
    )
  }

  private def auditAdminEvent(strideUser: String, identifiers: HO6Identifiers, response: HO6Response)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    adminService.auditAdminEvent(strideUser, identifiers, response)
  }

  private def fetchStatus(regId: String)(f: (String) => Future[Result]): Future[Result] = {
    ctService.fetchStatus(regId).semiflatMap(f).getOrElse(NotFound)
  }

  private def updateHeaderCarrierWithSessionId(sessionId: String)(implicit hc: HeaderCarrier) = {
    hc.copy(sessionId = Some(SessionId(sessionId)))
  }

  private def buildConfirmationRefs(identifiers: HO6Identifiers) = {
    ConfirmationReferences("", identifiers.transactionId, Some(identifiers.paymentReference), Some(identifiers.paymentAmount))
  }
}
