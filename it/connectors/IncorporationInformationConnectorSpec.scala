/*
 * Copyright 2021 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import itutil.{IntegrationSpecBase, WiremockHelper}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

class IncorporationInformationConnectorSpec extends IntegrationSpecBase {
  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.company-registration.protocol" -> s"https",
    "microservice.services.company-registration.host" -> s"test.host",
    "microservice.services.company-registration.port" -> s"1234",
    "microservice.services.incorporation-information.host" -> s"$mockHost",
    "microservice.services.incorporation-information.port" -> s"$mockPort"
  )
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val connector = app.injector.instanceOf[IncorporationInformationConnector]
  "callBackUrl" should {
    "be the correct url" in {
      connector.callBackurl(true) shouldBe "https://test.host:1234/company-registration/corporation-tax-registration/process-admin-incorp"
    }
  }

  "registerInterest" should {
    "return future successful true if admin is false" in {
      stubFor(post(urlEqualTo("/incorporation-information/subscribe/123/regime/ctax/subscriber/SCRS?force=true"))
        .withRequestBody(equalToJson(Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> connector.callBackurl(false))).toString()))
        .willReturn(
          aResponse().
            withStatus(202).
            withBody("""{ "f" :"c" }""")))

      await(connector.registerInterest("1", "123", false)(implicitly[HeaderCarrier], FakeRequest.apply(Call("", ""))))
    }
    "return future successful true if Admin is true" in {
      stubFor(post(urlEqualTo("/incorporation-information/subscribe/123/regime/ctax/subscriber/SCRS?force=true"))
        .withRequestBody(equalToJson(Json.obj("SCRSIncorpSubscription" -> Json.obj("callbackUrl" -> connector.callBackurl(true))).toString()))
        .willReturn(
          aResponse().
            withStatus(202).
            withBody("""{ "f" :"c" }""")))
      await(connector.registerInterest("1", "123", true)(implicitly[HeaderCarrier], FakeRequest.apply(Call("", ""))))
    }
    "return future failed" in {
      stubFor(post(urlEqualTo("/incorporation-information/subscribe/123/regime/ctax/subscriber/SCRS?force=true"))
        .willReturn(
          aResponse().
            withStatus(500).
            withBody("""{ "f" :"c" }""")))
      intercept[Exception](await(connector.registerInterest("1", "123", true)(implicitly[HeaderCarrier], FakeRequest.apply(Call("", "")))))
    }
  }
}