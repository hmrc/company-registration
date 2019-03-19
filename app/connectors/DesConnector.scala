/*
 * Copyright 2019 HM Revenue & Customs
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

import audit.DesSubmissionEventFailure
import config.MicroserviceAppConfig
import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.{JsObject, Writes}
import services.{AuditService, MetricsService}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DesConnectorImpl @Inject()(val metricsService: MetricsService,
                                 val http: HttpClient,
                                 microserviceAppConfig: MicroserviceAppConfig,
                                 val auditConnector: AuditConnector) extends DesConnector {

  lazy val serviceURL = microserviceAppConfig.baseUrl("des-service")
  lazy val urlHeaderEnvironment: String = microserviceAppConfig.getConfigString("des-service.environment")
  lazy val urlHeaderAuthorization: String = s"Bearer ${
    microserviceAppConfig.getConfigString("des-service.authorization-token")
  }"
}

trait DesConnector extends AuditService with RawResponseReads with HttpErrorFunctions {
  val serviceURL: String
  val baseURI = "/business-registration"
  val baseTopUpURI = "/business-incorporation"
  val ctRegistrationURI = "/corporation-tax"
  val ctRegistrationTopUpURI = "/corporation-tax"
  val http: HttpClient

  val urlHeaderEnvironment: String
  val urlHeaderAuthorization: String

  val metricsService: MetricsService

  def ctSubmission(ackRef: String, submission: JsObject, journeyId: String, isAdmin: Boolean = false)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val url: String = s"""${serviceURL}${baseURI}${ctRegistrationURI}"""
    metricsService.processDataResponseWithMetrics[HttpResponse](metricsService.desSubmissionCRTimer.time()) {
      cPOST(url, submission) map { response =>
        Logger.info(s"[DesConnector] [ctSubmission] Submission to DES successful for regId: $journeyId AckRef: $ackRef")
        sendCTRegSubmissionEvent(buildCTRegSubmissionEvent(ctRegSubmissionFromJson(journeyId, response.json.as[JsObject])))
        response
      } recoverWith {
        case ex: Upstream4xxResponse =>
          Logger.error("DES_SUBMISSION_400")
          Logger.warn(s"[DesConnector] [ctSubmission] Submission to DES was invalid for regId: $journeyId AckRef: $ackRef")
          val event = new DesSubmissionEventFailure(journeyId, submission)
          auditConnector.sendExtendedEvent(event)
          throw ex
      }
    }
  }

  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customDESRead(http, url, res)
  }

  def topUpCTSubmission(ackRef: String, submission: JsObject, journeyId: String, isAdmin: Boolean = false)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val url: String = s"$serviceURL$baseTopUpURI$ctRegistrationURI"
    metricsService.processDataResponseWithMetrics[HttpResponse](metricsService.desSubmissionCRTimer.time()) {
      cPOST(url, submission) map { response =>
        Logger.info(s"[DesConnector] [ctTopUpSubmission] Top up submission to DES successful for regId: $journeyId AckRef: $ackRef")
        sendCTRegSubmissionEvent(buildCTRegSubmissionEvent(ctRegSubmissionFromJson(journeyId, response.json.as[JsObject])))
        response
      } recoverWith {
        case ex: Upstream4xxResponse =>
          Logger.error("DES_SUBMISSION_400")
          Logger.warn(s"[DesConnector] [ctTopUpSubmission] Top up submission to DES was invalid for regId: $journeyId AckRef: $ackRef")
          val event = new DesSubmissionEventFailure(journeyId, submission)
          auditConnector.sendExtendedEvent(event)
          throw ex
      }
    }
  }

  @inline
  private def cPOST[I, O](url: String, body: I, headers: Seq[(String, String)] = Seq.empty)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier) =
    http.POST[I, O](url, body, headers)(wts = wts, rds = rds, hc = createHeaderCarrier(hc), implicitly)

  private def createHeaderCarrier(headerCarrier: HeaderCarrier): HeaderCarrier = {
    headerCarrier.
      withExtraHeaders("Environment" -> urlHeaderEnvironment).
      copy(authorization = Some(Authorization(urlHeaderAuthorization)))
  }

  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse) = {
    response.status match {
      case 409 =>
        Logger.warn("[DesConnector] [customDESRead] Received 409 from DES - converting to 202")
        HttpResponse(202, Some(response.json), response.allHeaders, Option(response.body))
      case 429 =>
        Logger.warn("[DesConnector] [customDESRead] Received 429 from DES - converting to 503")
        throw Upstream5xxResponse("Timeout received from DES submission", 499, 503)
      case 499 =>
        Logger.warn("[DesConnector] [customDESRead] Received 499 from DES - converting to 502")
        throw Upstream4xxResponse("Timeout received from DES submission", 499, 502)
      case status if is4xx(status) =>
        throw Upstream4xxResponse(upstreamResponseMessage(http, url, status, response.body), status, reportAs = 400, response.allHeaders)
      case _ => handleResponse(http, url)(response)
    }
  }
}
