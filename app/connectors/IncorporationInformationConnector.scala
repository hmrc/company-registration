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

package connectors

import javax.inject.Inject

import config.{MicroserviceAppConfig, WSHttp}
import play.api.Logger
import play.api.http.Status.ACCEPTED
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost}

import scala.concurrent.Future
import scala.util.control.NoStackTrace

class SubscriptionFailure(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

class IncorporationInformationConnectorImpl @Inject()(config: MicroserviceAppConfig) extends IncorporationInformationConnector {
  val url: String = config.incorpInfoUrl
  val http: WSGet with WSPost = WSHttp
  val regime: String = config.regime
  val subscriber: String = config.subscriber
}

trait IncorporationInformationConnector {

  val url: String
  val http: WSGet with WSPost

  val regime: String
  val subscriber: String

  private[connectors] def buildUri(transactionId: String): String = {
    s"/incorporation-information/subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true"
  }

  def registerInterest(regId: String, transactionId: String, admin: Boolean = false)(implicit hc: HeaderCarrier, req: Request[_]): Future[Boolean] = {
    val callbackUrl = if(admin) {
      s"${controllers.routes.ProcessIncorporationsController.processAdminIncorp().absoluteURL()}"
    } else {
      s"${controllers.routes.ProcessIncorporationsController.processIncorp().absoluteURL()}"
    }
    val json = Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> callbackUrl))
    http.POST[JsObject, HttpResponse](s"$url${buildUri(transactionId)}", json) map { res =>
      res.status match {
        case ACCEPTED =>
          Logger.info(s"[IncorporationInformationConnector] [registerInterest] Registration forced returned 202 for regId: $regId txId: $transactionId ")
          true
        case other    =>
          Logger.error(s"[IncorporationInformationConnector] [registerInterest] returned a $other response for regId: $regId txId: $transactionId")
          throw new RuntimeException(s"forced registration of interest for regId : $regId - transactionId : $transactionId failed - reason : status code was $other instead of 202")
      }
    } recover {
      case e =>
        Logger.error(s"[IncorporationInformationConnector] [registerInterest] failure registering interest for regId: $regId txId: $transactionId", e)
        throw new RuntimeException(s"forced registration of interest for regId : $regId - transactionId : $transactionId failed - reason : ", e)
    }
  }
}
