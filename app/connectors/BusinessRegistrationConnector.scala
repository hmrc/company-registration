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

package connectors

import connectors.httpParsers.BusinessRegistrationHttpParsers
import models.{BusinessRegistration, BusinessRegistrationRequest}
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessRegistrationConnectorImpl @Inject()(val http: HttpClient, servicesConfig: ServicesConfig
                                                 )(implicit val ec: ExecutionContext) extends BusinessRegistrationConnector {

  lazy val businessRegUrl: String = servicesConfig.baseUrl("business-registration")
}

sealed trait BusinessRegistrationResponse

case class BusinessRegistrationSuccessResponse(response: BusinessRegistration) extends BusinessRegistrationResponse

case object BusinessRegistrationNotFoundResponse extends BusinessRegistrationResponse

case object BusinessRegistrationForbiddenResponse extends BusinessRegistrationResponse

case class BusinessRegistrationErrorResponse(err: Exception) extends BusinessRegistrationResponse

trait BusinessRegistrationConnector extends BaseConnector with BusinessRegistrationHttpParsers with RawResponseReads {

  implicit val ec: ExecutionContext
  val businessRegUrl: String
  val http: HttpClient

  def createMetadataEntry(implicit hc: HeaderCarrier): Future[BusinessRegistration] =
    http.POST[BusinessRegistrationRequest, BusinessRegistration](s"$businessRegUrl/business-registration/business-tax-registration", BusinessRegistrationRequest("ENG"))(
      BusinessRegistrationRequest.formats, createBusinessRegistrationHttpParser, hc, ec
    )

  def retrieveMetadataByRegId(regId: String)(implicit hc: HeaderCarrier): Future[BusinessRegistrationResponse] =
    withMetadataRecovery("retrieveMetadata") {
      http.GET[BusinessRegistrationResponse](s"$businessRegUrl/business-registration/business-tax-registration/$regId")(
        retrieveBusinessRegistrationHttpParser(Some(regId)), hc, ec
      )
    }

  def retrieveMetadata(implicit hc: HeaderCarrier): Future[BusinessRegistrationResponse] =
    withMetadataRecovery("retrieveMetadata") {
      http.GET[BusinessRegistrationResponse](s"$businessRegUrl/business-registration/business-tax-registration")(
        retrieveBusinessRegistrationHttpParser(None), hc, ec
      )
    }

  def adminRetrieveMetadata(regId: String)(implicit hc: HeaderCarrier): Future[BusinessRegistrationResponse] =
    withMetadataRecovery("adminRetrieveMetadata") {
      http.GET[BusinessRegistrationResponse](s"$businessRegUrl/business-registration/admin/business-tax-registration/$regId")(
        retrieveBusinessRegistrationHttpParser(Some(regId)), hc, ec
      )
    }

  def removeMetadata(registrationId: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    withRecovery(Some(false))("removeMetadata", Some(registrationId)) {
      http.GET[Boolean](s"$businessRegUrl/business-registration/business-tax-registration/remove/$registrationId")(removeMetadataHttpReads(registrationId), hc, ec)
    }

  def adminRemoveMetadata(registrationId: String): Future[Boolean] =
    withRecovery()("adminRemoveMetadata") {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      http.GET[Boolean](s"$businessRegUrl/business-registration/admin/business-tax-registration/remove/$registrationId")(removeMetadataAdminHttpReads(registrationId), hc, ec)
    }

  def dropMetadataCollection(implicit hc: HeaderCarrier): Future[String] =
    withRecovery()("dropMetadataCollection") {
      http.GET[String](s"$businessRegUrl/business-registration/test-only/drop-collection")(dropMetadataCollectionHttpReads, hc, ec)
    }

  def updateLastSignedIn(registrationId: String, timestamp: Instant)(implicit hc: HeaderCarrier): Future[String] =
    withRecovery()("updateLastSignedIn", Some(registrationId)) {
      http.PATCH[Instant, HttpResponse](s"$businessRegUrl/business-registration/business-tax-registration/last-signed-in/$registrationId", timestamp).map {
        res => res.body
      }
    }

  private def withMetadataRecovery(functionName: String)(f: => Future[BusinessRegistrationResponse]): Future[BusinessRegistrationResponse] = f recover {
    case e: Exception =>
      logger.error(s"[$functionName] Received error when expecting metadata from Business-Registration - Error ${e.getMessage}")
      BusinessRegistrationErrorResponse(e)
  }
}
