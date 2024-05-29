/*
 * Copyright 2024 HM Revenue & Customs
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

package connectors.httpParsers

import connectors.{BaseConnector, BusinessRegistrationForbiddenResponse, BusinessRegistrationNotFoundResponse, BusinessRegistrationResponse, BusinessRegistrationSuccessResponse}
import models.BusinessRegistration
import play.api.http.Status.{FORBIDDEN, NOT_FOUND, OK}
import play.api.libs.json.__
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

trait BusinessRegistrationHttpParsers extends BaseHttpReads { _: BaseConnector =>

  val createBusinessRegistrationHttpParser: HttpReads[BusinessRegistration] =
    httpReads[BusinessRegistration]("createBusinessRegistrationHttpParser")

  def retrieveBusinessRegistrationHttpParser(regId: Option[String]): HttpReads[BusinessRegistrationResponse] =
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK =>
          val busReg = jsonParse[BusinessRegistration](response)("retrieveBusinessRegistrationHttpParser", regId)
          BusinessRegistrationSuccessResponse(busReg)
        case NOT_FOUND =>
          logger.info(s"[retrieveBusinessRegistrationHttpParser] Received a NotFound status code when expecting metadata from Business-Registration" + logContext(regId))
          BusinessRegistrationNotFoundResponse
        case FORBIDDEN =>
          logger.error(s"[retrieveBusinessRegistrationHttpParser] Received a Forbidden status code when expecting metadata from Business-Registration" + logContext(regId))
          BusinessRegistrationForbiddenResponse
        case status =>
          unexpectedStatusHandling()("retrieveBusinessRegistrationHttpParser", url, status, regId)
      }

  def removeMetadataHttpReads(regId: String): HttpReads[Boolean] =
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK => true
        case NOT_FOUND =>
          logger.info(s"[removeMetadataHttpReads] Received a NotFound status code when attempting to remove a metadata document" + logContext(Some(regId)))
          false
        case status =>
          unexpectedStatusHandling(Some(false))("removeMetadataHttpReads", url, status, Some(regId))
      }

  def removeMetadataAdminHttpReads(regId: String): HttpReads[Boolean] =
    (_: String, url: String, response: HttpResponse) =>
      response.status match {
        case OK => true
        case NOT_FOUND =>
          logger.info(s"[removeMetadataAdminHttpReads] Received a NotFound status code when attempting to remove a metadata document" + logContext(Some(regId)))
          false
        case status =>
          unexpectedStatusHandling()("removeMetadataAdminHttpReads", url, status, Some(regId))
      }

  val dropMetadataCollectionHttpReads: HttpReads[String] =
    httpReads[String]("dropMetadataCollectionHttpReads")((__ \ "message").read[String], manifest[String])

}

object BusinessRegistrationHttpParsers extends BusinessRegistrationHttpParsers with BaseConnector
