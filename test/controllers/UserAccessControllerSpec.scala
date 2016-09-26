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

import connectors.AuthConnector
import fixtures.AuthFixture
import helpers.SCRSSpec
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.UserAccessService
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.test.Helpers._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class UserAccessControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication with SCRSSpec with AuthFixture{

  val mockUserAccessService = mock[UserAccessService]

  implicit val hc = HeaderCarrier()

  trait Setup {
    val controller = new UserAccessController {
      override val userAccessService: UserAccessService = mockUserAccessService
      override val auth: AuthConnector = mockAuthConnector
    }
  }

  "UserAccessController" should {
    "use the correct auth connector" in {
      UserAccessController.auth shouldBe AuthConnector
    }
    "use the correct service" in{
      UserAccessController.userAccessService shouldBe UserAccessService
    }
  }

  "checkUserAccess" should {
    "return a forbidden status code" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      status(controller.checkUserAccess(FakeRequest())) shouldBe FORBIDDEN
    }
    "return a future JsValue" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockUserAccessService.checkUserAccess(Matchers.anyString())(Matchers.any()))
        .thenReturn(Future.successful(Json.parse("""{ "test" : 123, "testerer": "string"}""")))
      val result = controller.checkUserAccess(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.parse("""{"test":123,"testerer":"string"}""")
    }
  }

}
