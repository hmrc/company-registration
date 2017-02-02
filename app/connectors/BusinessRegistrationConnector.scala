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

package connectors

import config.WSHttp
import models.{BusinessRegistration, BusinessRegistrationRequest}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object BusinessRegistrationConnector extends BusinessRegistrationConnector with ServicesConfig {
  val businessRegUrl = baseUrl("business-registration")
  val http = WSHttp
}

sealed trait BusinessRegistrationResponse
case class BusinessRegistrationSuccessResponse(response: BusinessRegistration) extends BusinessRegistrationResponse
case object BusinessRegistrationNotFoundResponse extends BusinessRegistrationResponse
case object BusinessRegistrationForbiddenResponse extends BusinessRegistrationResponse
case class BusinessRegistrationErrorResponse(err: Exception) extends BusinessRegistrationResponse

trait BusinessRegistrationConnector {

  val businessRegUrl: String
  val http: HttpGet with HttpPost with HttpPatch

  def createMetadataEntry(implicit hc: HeaderCarrier): Future[BusinessRegistration] = {
    val json = Json.toJson[BusinessRegistrationRequest](BusinessRegistrationRequest("ENG"))
    http.POST[JsValue, BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration", json)
  }

  def retrieveMetadata(implicit hc: HeaderCarrier, rds: HttpReads[BusinessRegistration]): Future[BusinessRegistrationResponse] = {
    http.GET[BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration") map {
      metaData =>
        BusinessRegistrationSuccessResponse(metaData)
    } recover {
      case e: NotFoundException =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received a NotFound status code when expecting metadata from Business-Registration")
        BusinessRegistrationNotFoundResponse
      case e: ForbiddenException =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received a Forbidden status code when expecting metadata from Business-Registration")
        BusinessRegistrationForbiddenResponse
      case e: Exception =>
        Logger.error(s"[BusinessRegistrationConnector] [retrieveMetadata] - Received error when expecting metadata from Business-Registration - Error ${e.getMessage}")
        BusinessRegistrationErrorResponse(e)
    }
  }

  def removeMetadata(registrationId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    http.GET[HttpResponse](s"$businessRegUrl/business-registration/business-tax-registration/remove/$registrationId").map {
      _.status match {
        case 200 => true
      }
    } recover {
      case ex: NotFoundException =>
        Logger.error(s"[BusinessRegistrationConnector] [removeMetadata] - Received a NotFound status code when attempting to remove a metadata document for regId - $registrationId")
        false
    }
  }

  def dropMetadataCollection(implicit hc: HeaderCarrier) = {
    http.GET[JsValue](s"$businessRegUrl/business-registration/test-only/drop-collection") map { res =>
      (res \ "message").as[String]
    }
  }

  def updateLastSignedIn(registrationId: String, dateTime: DateTime)(implicit hc: HeaderCarrier): Future[String] = {
    val json = Json.toJson(dateTime)
    http.PATCH[JsValue, HttpResponse](s"$businessRegUrl/business-registration/business-tax-registration/last-signed-in/$registrationId", json).map{
      res => res.body
    } recover {
      case ex: HttpException =>
        Logger.error(s"[BusinessRegistrationConnector] [updateLastSignedIn] - ${ex.responseCode} Could not update lastSignedIn for regId: $registrationId - reason: ${ex.getMessage}")
        throw new Exception(ex.getMessage)
    }
  }
}
