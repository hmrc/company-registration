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

package controllers

import config.WSHttp
import helpers.SCRSSpec
import mocks.WSHttpMock
import org.scalatest.{BeforeAndAfter, BeforeAndAfterEach}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.WithFakeApplication

class DesControllerSpec extends SCRSSpec with WSHttpMock with WithFakeApplication {

  trait Setup {
    val controller = new DesController with ServicesConfig {
      override val desUrl = "test"
      override val http = mockWSHttp
    }
  }

  "DesController" should {

    "use the correct Des Url" in new Setup {
      DesController.desUrl shouldBe "http://localhost:9642/business-registration-dynamic-stub/des-stub"
    }

    "use the correct WSHttp" in new Setup {
      DesController.http shouldBe WSHttp
    }

  }

  "reading the coho url from config" should {

    "" in {

    }
  }

  "submit" should {

    "return a 202 status" in new Setup {
      val response = HttpResp(202, "Response message")
      val request = FakeRequest().withJsonBody(Json.toJson("{}"))
      mockHttpPOST(controller.desUrl, response)
      status(call(controller.submit, request)) shouldBe ACCEPTED
    }

    "return a 400 status" in new Setup {
      val response = HttpResp(400, "Response message")
      val request = FakeRequest().withJsonBody(Json.toJson("{}"))
      mockHttpPOST(controller.desUrl, response)
      status(call(controller.submit, request)) shouldBe BAD_REQUEST
    }

    "return a 400 status when body is not Json" in new Setup {
      val request = FakeRequest().withBody("{}")
      status(call(controller.submit, request)) shouldBe BAD_REQUEST
    }

  }

}
