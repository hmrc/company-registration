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

package services.admin

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import javax.inject.{Inject, Singleton}

import audit.AdminReleaseAuditEvent
import config.MicroserviceAuditConnector
import helpers.{DateFormatter, DateHelper}
import models.HO6RegistrationInformation
import models.admin.{HO6Identifiers, HO6Response}
import play.api.libs.json.{JsObject, Json}
import repositories.{CorpTaxRegistrationRepo, CorporationTaxRegistrationMongoRepository}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class AdminServiceImpl @Inject()(corpTaxRepo: CorpTaxRegistrationRepo) extends AdminService {
  val repo: CorporationTaxRegistrationMongoRepository = corpTaxRepo.repo
  val auditConnector = MicroserviceAuditConnector
}

trait AdminService extends DateFormatter {

  val repo: CorporationTaxRegistrationMongoRepository
  val auditConnector: AuditConnector

  def fetchHO6RegistrationInformation(regId: String): Future[Option[HO6RegistrationInformation]] = repo.fetchHO6Information(regId)

  def auditAdminEvent(strideUser: String, identifiers: HO6Identifiers, response: HO6Response)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    val identifiersJson = Json.toJson(identifiers)(HO6Identifiers.adminAuditWrites).as[JsObject]
    val responseJson = Json.toJson(response)(HO6Response.adminAuditWrites).as[JsObject]
    val timestamp = Json.obj("timestamp" -> Json.toJson(nowAsZonedDateTime)(zonedDateTimeWrites))
    val auditEvent = new AdminReleaseAuditEvent(timestamp, strideUser, identifiersJson, responseJson)
    auditConnector.sendEvent(auditEvent)
  }
}
