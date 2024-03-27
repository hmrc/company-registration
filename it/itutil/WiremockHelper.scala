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

package itutil

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import play.api.http.Status
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.WWW_AUTHENTICATE

object WiremockHelper extends Eventually with IntegrationPatience {
  val wiremockPort = 11111
  val wiremockHost = "localhost"
  val url = s"http://$wiremockHost:$wiremockPort"

  def stubGet(url: String, status: Integer, body: String) =
    stubFor(get(urlMatching(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(body)
      )
    )

  def verifyPOSTRequestBody(url: String, json: String): Boolean = {
    import collection.JavaConverters._

    findAll(postRequestedFor(urlMatching(url))).asScala.toList.map(_.getBodyAsString).exists(_.contains(json))
  }

  def stubDelete(url: String, status: Integer, body: String) =
    stubFor(delete(urlEqualTo(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(body)
      )
    )

  def stubPost(url: String, status: Integer, responseBody: String) = {
    removeStub(post(urlMatching(url)))
    stubFor(post(urlMatching(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(responseBody)
      )
    )
  }

  def stubPatch(url: String, status: Integer, responseBody: String) =
    stubFor(patch(urlMatching(url))
      .willReturn(
        aResponse().
          withStatus(status).
          withBody(responseBody)
      )
    )

  def stubAuthorise(status: Int, body: (String, String)*) = {
    stubFor(post(urlMatching("/auth/authorise"))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withBody{
            Json.obj(body.map{ case (k, v) => k -> Json.toJsFieldJsValueWrapper(v)}:_*).toString()
          }
      ))
  }

  def stubAuthorise(status: Int, body: JsObject) = {
    stubFor(post(urlMatching("/auth/authorise"))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withBody(body.toString())
      ))
  }

  def stubAuthorise(internalId: String): StubMapping = stubAuthorise(200, "internalId" -> internalId)

  def stubUnauthorised(reason: String) = {
    stubFor(post(urlMatching("/auth/authorise"))
      .willReturn(
        aResponse()
          .withStatus(Status.UNAUTHORIZED)
          .withHeader(WWW_AUTHENTICATE, s"""MDTP detail="$reason"""")
      ))
  }

  def stubAudit() =
    stubFor(post(urlPathMatching("/write/audit.*")).willReturn(aResponse().withStatus(Status.OK)))

}

trait WiremockHelper {
  import WiremockHelper._
  lazy val wmConfig = wireMockConfig().port(wiremockPort)
  lazy val wireMockServer = new WireMockServer(wmConfig)


  def startWiremock() = {
    wireMockServer.start()
    WireMock.configureFor(wiremockHost, wiremockPort)
  }

  def stopWiremock() = wireMockServer.stop()

  def resetWiremock() = WireMock.reset()
}