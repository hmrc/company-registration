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

package connectors

import config.WSHttp
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsObject, Writes}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

sealed trait Response
case object SuccessResponse extends Response
case object NotFoundResponse extends Response
case object ConflictResponse extends Response
case object InvalidRequest extends Response


trait DesConnector extends ServicesConfig with RawResponseReads {

  lazy val serviceURL = baseUrl("des-service")
  val baseURI = "/business-registration"
  val ctRegistrationURI = "/corporation-tax"

  val urlHeaderEnvironment: String
  val urlHeaderAuthorization: String

  val http: HttpGet with HttpPost with HttpPut = WSHttp

  def ctSubmission(ackRef:String, submission: JsObject)(implicit headerCarrier: HeaderCarrier): Future[Response] = {
    val response = cPOST(s"""${serviceURL}${baseURI}${ctRegistrationURI}""", submission)
    response map { r =>
      r.status match {
        case OK => SuccessResponse
        case ACCEPTED => SuccessResponse
        case CONFLICT => {
          Logger.warn(s"ETMP reported a duplicate submission for ack ref ${ackRef}")
          SuccessResponse
        }
        case NOT_FOUND => NotFoundResponse
        case BAD_REQUEST => {
          val message = (r.json \ "reason").toString
          Logger.warn(s"ETMP reported an error with the request ${message}")
          InvalidRequest
        }
        case _ => InvalidRequest // TODO SCRS-2298
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
  override val urlHeaderEnvironment: String = config("des-service").getString("environment").getOrElse("")
  override val urlHeaderAuthorization: String = s"Bearer ${config("des-service").getString("authorization-token").getOrElse("")}"
}
