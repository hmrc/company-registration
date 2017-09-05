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

import audit.DesSubmissionEventFailure
import config.{MicroserviceAuditConnector, WSHttp}
import play.api.{Logger, Play}
import play.api.libs.json.{JsObject, Writes}
import services.{AuditService, MetricsService}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DesConnector extends ServicesConfig with AuditService with RawResponseReads with HttpErrorFunctions {

  lazy val serviceURL = baseUrl("des-service")
  val baseURI = "/business-registration"
  val ctRegistrationURI = "/corporation-tax"

  val urlHeaderEnvironment: String
  val urlHeaderAuthorization: String

  val http: HttpGet with HttpPost with HttpPut = WSHttp

  val metricsService: MetricsService

  private[connectors] def customDESRead(http: String, url: String, response: HttpResponse) = {
    response.status match {
      case 409 =>
        Logger.warn("[DesConnector] [customDESRead] Received 409 from DES - converting to 202")
        HttpResponse(202, Some(response.json), response.allHeaders, Option(response.body))
      case 499 =>
        Logger.warn("[DesConnector] [customDESRead] Received 499 from DES - converting to 502")
        throw Upstream4xxResponse("Timeout received from DES submission", 499, 502)
      case status if is4xx(status) =>
        throw Upstream4xxResponse(upstreamResponseMessage(http, url, status, response.body), status, reportAs = 400, response.allHeaders)
      case _ => handleResponse(http, url)(response)
    }
  }

  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customDESRead(http, url, res)
  }

  def ctSubmission(ackRef:String, submission: JsObject, journeyId : String, isAdmin: Boolean = false)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val url: String = s"""${serviceURL}${baseURI}${ctRegistrationURI}"""
    metricsService.processDataResponseWithMetrics[HttpResponse](metricsService.desSubmissionCRTimer.time()) {
      cPOST(url, submission) map { response =>
        sendCTRegSubmissionEvent(buildCTRegSubmissionEvent(ctRegSubmissionFromJson(journeyId, response.json.as[JsObject])))
        response
      } recoverWith {
        case ex: Upstream4xxResponse =>
          val event = new DesSubmissionEventFailure(journeyId, submission)
          auditConnector.sendEvent(event)
          throw ex
      }
    }
  }

  private def createHeaderCarrier(headerCarrier: HeaderCarrier): HeaderCarrier = {
    headerCarrier.
      withExtraHeaders("Environment" -> urlHeaderEnvironment).
      copy(authorization = Some(Authorization(urlHeaderAuthorization)))
  }

  @inline
  private def cPOST[I, O](url: String, body: I, headers: Seq[(String, String)] = Seq.empty)(implicit wts: Writes[I], rds: HttpReads[O], hc: HeaderCarrier) =
    http.POST[I, O](url, body, headers)(wts = wts, rds = rds, hc = createHeaderCarrier(hc))

}

object DesConnector extends DesConnector {
  // $COVERAGE-OFF$
  val auditConnector = MicroserviceAuditConnector
  val urlHeaderEnvironment: String = getConfString("des-service.environment", throw new Exception("could not find config value for des-service.environment"))
  val urlHeaderAuthorization: String = s"Bearer ${getConfString("des-service.authorization-token",
    throw new Exception("could not find config value for des-service.authorization-token"))}"

  val metricsService = Play.current.injector.instanceOf[MetricsService]
  // $COVERAGE-ON$
}
