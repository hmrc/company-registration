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

import javax.inject.{Inject, Singleton}

import config.{MicroserviceAppConfig, WSHttp}
import models.IncorpStatus
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost}
import play.api.http.Status.{ACCEPTED, OK}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

class SubscriptionFailure(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class IncorporationInformationConnectorImpl @Inject()(config: MicroserviceAppConfig) extends IncorporationInformationConnector {
  val url: String = config.incorpInfoUrl
  val http: WSGet with WSPost = WSHttp
  val regime: String = config.regime
  val subscriber: String = config.subscriber
}

object IncorporationInformationConnector extends IncorporationInformationConnector with ServicesConfig {
  val url: String = baseUrl("incorporation-information")
  val http: WSGet with WSPost = WSHttp
  val regime: String = getConfString("regime", throw new RuntimeException("[IncorporationInformationConnector] Could not find regime in config"))
  val subscriber: String = getConfString("subscriber", throw new RuntimeException("[IncorporationInformationConnector]Could not find subscriber in config"))
}

trait IncorporationInformationConnector {

  val url: String
  val http: WSGet with WSPost

  val regime: String
  val subscriber: String

  private[connectors] def buildUri(transactionId: String): String = {
    s"/incorporation-information/subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true"
  }

  def registerInterest(regId: String, transactionId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val json = Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> s"${controllers.routes.ProcessIncorporationsController.processIncorp()} "))
    http.POST[JsObject, HttpResponse](s"$url${buildUri(transactionId)}", json) map { res =>
      res.status match {
        case ACCEPTED =>
          Logger.info(s"[IncorporationInformationConnector] [registerInterest] Registration forced returned 202 for regId: $regId txId: $transactionId ")
          true
        case other    =>
          Logger.error(s"[IncorporationInformationConnector] [registerInterest] returned a $other response for regId: $regId txId: $transactionId")
          false
      }
    } recover {
      case e =>
        Logger.error(s"[IncorporationInformationConnector] [registerInterest] failure registering interest for regId: $regId txId: $transactionId", e)
        false
    }
  }
}