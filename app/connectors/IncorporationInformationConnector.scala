/*
 * Copyright 2022 HM Revenue & Customs
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

import config.MicroserviceAppConfig
import play.api.http.Status.{ACCEPTED, NO_CONTENT, OK}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.http.HttpClient
import utils.{AlertLogging, PagerDutyKeys}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class SubscriptionFailure(msg: String) extends NoStackTrace {
  override def getMessage: String = msg
}

@Singleton
class IncorporationInformationConnectorImpl @Inject()(config: MicroserviceAppConfig,
                                                      val http: HttpClient
                                                     )(implicit val ec: ExecutionContext) extends IncorporationInformationConnector {
  lazy val iiUrl: String = config.incorpInfoUrl
  lazy val companyRegUrl = config.compRegUrl
  lazy val regime: String = config.regime
  lazy val subscriber: String = config.subscriber
}

trait IncorporationInformationConnector extends AlertLogging {

  implicit val ec: ExecutionContext
  val iiUrl: String
  val http: HttpClient
  val companyRegUrl: String
  val regime: String
  val subscriber: String

  private[connectors] val callBackurl = (isAdmin: Boolean) => {
    companyRegUrl + {
      if (isAdmin) {
        controllers.routes.ProcessIncorporationsController.processAdminIncorporation.url
      } else {
        controllers.routes.ProcessIncorporationsController.processIncorporationNotification.url
      }
    }
  }

  private[connectors] def buildUri(transactionId: String): String = {
    s"/incorporation-information/subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true"
  }

  private[connectors] def buildCancelUri(transactionId: String): String = {
    s"/incorporation-information/subscribe/$transactionId/regime/ct/subscriber/$subscriber?force=true"
  }

  def registerInterest(regId: String, transactionId: String, admin: Boolean = false)(implicit hc: HeaderCarrier, req: Request[_]): Future[Boolean] = {
    val json = Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> callBackurl(admin)))
    http.POST[JsObject, HttpResponse](s"$iiUrl${buildUri(transactionId)}", json) map { res =>
      res.status match {
        case ACCEPTED =>
          logger.info(s"[registerInterest] Registration forced returned 202 for regId: $regId txId: $transactionId ")
          true
        case other =>
          logger.error(s"[registerInterest] returned a $other response for regId: $regId txId: $transactionId")
          throw new RuntimeException(s"forced registration of interest for regId : $regId - transactionId : $transactionId failed - reason : status code was $other instead of 202")
      }
    } recover {
      case e =>
        logger.error(s"[registerInterest] failure registering interest for regId: $regId txId: $transactionId", e)
        throw new RuntimeException(s"forced registration of interest for regId : $regId - transactionId : $transactionId failed - reason : ", e)
    }
  }

  def cancelSubscription(regId: String, transactionId: String, useOldRegime: Boolean = false)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val cancelUri = if (useOldRegime) buildCancelUri(transactionId) else buildUri(transactionId)

    http.DELETE[HttpResponse](s"$iiUrl$cancelUri") map { res =>
      res.status match {
        case OK =>
          logger.info(s"[cancelSubscription] Cancelled subscription for regId: $regId txId: $transactionId ")
          true
      }
    } recover {
      case e: NotFoundException =>
        logger.info(s"[cancelSubscription] No subscription to cancel for regId: $regId txId: $transactionId ")
        throw e
      case e =>
        logger.error(s"[cancelSubscription] Error cancelling subscription for regId: $regId txId: $transactionId", e)
        throw new RuntimeException(s"Failure to cancel subscription", e)
    }
  }

  def checkCompanyIncorporated(transactionID: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    http.GET[HttpResponse](s"$iiUrl/incorporation-information/$transactionID/incorporation-update") map { res =>
      res.status match {
        case OK =>
          val crn = (res.json \ "crn").asOpt[String]
          if (crn.nonEmpty) {
            pagerduty(PagerDutyKeys.STALE_DOCUMENTS_DELETE_WARNING_CRN_FOUND)
          }
          crn
        case NO_CONTENT => None
      }
    }
  }
}
