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
import play.api.http.Status._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OidExtractor {
  def userIdToOid(userId: String): String = userId.substring(userId.lastIndexOf("/") + 1)
}

case class Authority(
                      uri: String,
                      oid: String,
                      userDetailsLink: String
                    )

trait AuthConnector extends ServicesConfig with RawResponseReads {

  def serviceUrl: String

  def authorityUri: String

  def http: HttpGet with HttpPost

  def getCurrentAuthority()(implicit headerCarrier: HeaderCarrier): Future[Option[Authority]] = {
    val getUrl = s"""$serviceUrl/$authorityUri"""
    Logger.debug(s"[AuthConnector][getCurrentAuthority] - GET $getUrl")
    http.GET[HttpResponse](getUrl).map {
      response =>
        Logger.debug(s"[AuthConnector][getCurrentAuthority] - RESPONSE status: ${response.status}, body: ${response.body}")
        response.status match {
          case OK => {
            val uri = (response.json \ "uri").as[String]
            val oid = OidExtractor.userIdToOid(uri)
            val userDetails = (response.json \ "userDetailsLink").as[String]
            Some(Authority(uri, oid, userDetails))
          }
          case status => None
        }
    }
  }

}

object AuthConnector extends AuthConnector {
  // $COVERAGE-OFF$
  lazy val serviceUrl = baseUrl("auth")
  // $COVERAGE-OFF$
  val authorityUri = "auth/authority"
  val http: HttpGet with HttpPost = WSHttp
}
