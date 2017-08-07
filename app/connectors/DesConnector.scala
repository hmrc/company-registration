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
import play.api.http.Status._
import play.api.libs.json.{JsObject, Writes}
import services.{AuditService, MetricsService}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait DesResponse
case class SuccessDesResponse(response: JsObject) extends DesResponse
case object NotFoundDesResponse extends DesResponse
case object DesErrorResponse extends DesResponse
case class InvalidDesRequest(message: String) extends DesResponse

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
      case 400 => response
      case 404 => throw new NotFoundException("ETMP returned a Not Found status")
      case 409 => response
      case 500 => throw new InternalServerException("ETMP returned an internal server error")
      case 502 => throw new BadGatewayException("ETMP returned an upstream error")
      case _ => handleResponse(http, url)(response)
    }
  }

  implicit val httpRds = new HttpReads[HttpResponse] {
    def read(http: String, url: String, res: HttpResponse) = customDESRead(http, url, res)
  }

  def ctSubmission(ackRef:String, submission: JsObject, journeyId : String, isAdmin: Boolean = false)(implicit headerCarrier: HeaderCarrier): Future[DesResponse] = {
    val url: String = s"""${serviceURL}${baseURI}${ctRegistrationURI}"""
    val timer = metricsService.desSubmissionCRTimer.time()
    val response = cPOST(url, submission)
    response flatMap { r =>
      sendCTRegSubmissionEvent(buildCTRegSubmissionEvent(ctRegSubmissionFromJson(journeyId, r.json.as[JsObject])))
      r.status match {
        case OK =>
          timer.stop()
          Logger.info(s"Successful Des submission for ackRef ${ackRef} to ${url}")
          Future.successful(SuccessDesResponse(r.json.as[JsObject]))
        case ACCEPTED =>
          timer.stop()
          Logger.info(s"Accepted Des submission for ackRef ${ackRef} to ${url}")
          Future.successful(SuccessDesResponse(r.json.as[JsObject]))
        case CONFLICT => {
          timer.stop()
          Logger.warn(s"ETMP reported a duplicate submission for ack ref ${ackRef}")
          Future.successful(SuccessDesResponse(r.json.as[JsObject]))
        }
        case BAD_REQUEST => {
          timer.stop()
          val message = (r.json \ "reason").as[String]
          Logger.warn(s"ETMP reported an error with the request ${message}")
          val event = new DesSubmissionEventFailure(journeyId, submission)
          auditConnector.sendEvent(event) map {
            _ => InvalidDesRequest(message)
          }
        }
      }
    } recover {
      case ex: NotFoundException =>
        timer.stop()
        Logger.warn(s"ETMP reported a not found for ack ref ${ackRef}")
        NotFoundDesResponse
      case ex: InternalServerException =>
        timer.stop()
        Logger.warn(s"ETMP reported an internal server error status for ack ref ${ackRef}")
        DesErrorResponse
      case ex: BadGatewayException =>
        timer.stop()
        Logger.warn(s"ETMP reported a bad gateway status for ack ref ${ackRef}")
        DesErrorResponse
      case ex: Exception =>
        timer.stop()
        Logger.warn(s"ETMP reported a ${ex.toString} for ack ref ${ackRef}")
        DesErrorResponse
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
