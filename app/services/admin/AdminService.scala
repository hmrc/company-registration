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

package services.admin

import javax.inject.{Inject, Singleton}

import audit.{AdminCTReferenceEvent, AdminReleaseAuditEvent}
import config.MicroserviceAuditConnector
import connectors.{BusinessRegistrationConnector, IncorporationInformationConnector}
import helpers.DateFormatter
import models.HO6RegistrationInformation
import models.admin.{AdminCTReferenceDetails, HO6Identifiers, HO6Response}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import repositories.{CorpTaxRegistrationRepo, CorporationTaxRegistrationMongoRepository, HeldSubmissionMongoRepository, HeldSubmissionRepo}
import services.FailedToDeleteSubmissionData
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class AdminServiceImpl @Inject()(
                                 corpTaxRepo: CorpTaxRegistrationRepo,
                                 heldSubMongo: HeldSubmissionRepo,
                                 val brConnector: BusinessRegistrationConnector,
                                 val incorpInfoConnector: IncorporationInformationConnector) extends AdminService {

  val corpTaxRegRepo: CorporationTaxRegistrationMongoRepository = corpTaxRepo.repo
  val heldSubRepo: HeldSubmissionMongoRepository = heldSubMongo.store
  val auditConnector = MicroserviceAuditConnector
}

trait AdminService extends DateFormatter {

  val corpTaxRegRepo:      CorporationTaxRegistrationMongoRepository
  val heldSubRepo:         HeldSubmissionMongoRepository
  val auditConnector:      AuditConnector
  val incorpInfoConnector: IncorporationInformationConnector
  val brConnector:         BusinessRegistrationConnector

  def fetchHO6RegistrationInformation(regId: String): Future[Option[HO6RegistrationInformation]] = corpTaxRegRepo.fetchHO6Information(regId)

  def migrateHeldSubmissions(implicit hc: HeaderCarrier, req: Request[_]): Future[List[Boolean]] = {
    fetchAllRegIdsFromHeldSubmissions flatMap { regIdList =>
      Future.sequence(regIdList map { regId =>
        fetchTransactionId(regId) flatMap { opt =>
          opt.fold(Future.successful(false))(transId =>
            forceSubscription(regId, transId) recover {
              case ex: RuntimeException =>
                Logger.error(s"[Admin] [migrateHeldSubmissions] - force subscription for regId : $regId failed", ex)
                false
            }
          )
        }
      })
    }
  }

  private[services] def forceSubscription(regId: String, transactionId: String)(implicit hc: HeaderCarrier, req: Request[_]): Future[Boolean] = {
    incorpInfoConnector.registerInterest(regId, transactionId, true)
  }

  def auditAdminEvent(strideUser: String, identifiers: HO6Identifiers, response: HO6Response)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    val identifiersJson = Json.toJson(identifiers)(HO6Identifiers.adminAuditWrites).as[JsObject]
    val responseJson = Json.toJson(response)(HO6Response.adminAuditWrites).as[JsObject]
    val timestamp = Json.obj("timestamp" -> Json.toJson(nowAsZonedDateTime)(zonedDateTimeWrites))
    val auditEvent = new AdminReleaseAuditEvent(timestamp, strideUser, identifiersJson, responseJson)
    auditConnector.sendExtendedEvent(auditEvent)
  }

  private[services] def fetchAllRegIdsFromHeldSubmissions: Future[List[String]] = heldSubRepo.findAll() map { list => list.map(_.registrationID)}

  private[services] def fetchTransactionId(regId: String): Future[Option[String]] = corpTaxRegRepo.retrieveConfirmationReferences(regId).map(_.fold[Option[String]]{
    Logger.error(s"[Admin] [fetchTransactionId] - Held submission found but transaction Id missing for regId $regId")
    None
  }(refs => Option(refs.transactionId)))

  def ctutrCheck(id: String): Future[JsObject] = {
    corpTaxRegRepo.retrieveStatusAndExistenceOfCTUTR(id) map {
      case Some((status, ctutr)) => Json.obj("status" -> status, "ctutr" -> ctutr)
      case _ => Json.obj()
    }
  }

  def updateRegistrationWithCTReference(ackRef : String, ctUtr : String, username : String)(implicit hc : HeaderCarrier) : Future[Option[JsObject]] = {
    corpTaxRegRepo.updateRegistrationWithAdminCTReference(ackRef, ctUtr) map { _ flatMap { cr =>
        cr.acknowledgementReferences map { acknowledgementRefs =>
          val timestamp = Json.obj("timestamp" -> Json.toJson(nowAsZonedDateTime)(zonedDateTimeWrites))
          val refDetails = AdminCTReferenceDetails(acknowledgementRefs.ctUtr, ctUtr,acknowledgementRefs.status,"04")

          auditConnector.sendExtendedEvent(
            new AdminCTReferenceEvent(timestamp, username, Json.toJson(refDetails)(AdminCTReferenceDetails.adminAuditWrites).as[JsObject])
          )

          Json.obj("status" -> "04", "ctutr" -> true)
        }
      }
    }
  }


  def deleteRejectedSubmissionData(regId: String): Future[Boolean] = {
    for {
      heldDeleted <- heldSubRepo.removeHeldDocument(regId)
    } yield {
      if (heldDeleted) {
        Logger.info(s"[deleteRejectedSubmissionData] Successfully deleted registration with regId: $regId")
        true
      } else {
        Logger.error(s"[deleteRejectedSubmissionData] Failed to delete registration with regId: $regId")
        throw FailedToDeleteSubmissionData
      }
    }
  }

  def updateTransactionId(updateFrom: String, updateTo: String): Future[Boolean] = {
    corpTaxRegRepo.updateTransactionId(updateFrom, updateTo) map {
      _ == updateTo
    } recover {
      case _ => false
    }
  }
}
