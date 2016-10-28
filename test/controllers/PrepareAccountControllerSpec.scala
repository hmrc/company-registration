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

import connectors.{AuthConnector, Authority}
import helpers.SCRSSpec
import models.PrepareAccountModel
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.{Json, JsValue}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.Repositories
import services.PrepareAccountService

import scala.concurrent.Future

class PrepareAccountControllerSpec extends SCRSSpec {

  val mockPrepareAccountService = mock[PrepareAccountService]

  class Setup {
    val prepareAccountController = new PrepareAccountController {
      override val service = mockPrepareAccountService
      override val resourceConn = mockCTDataRepository
      override val auth = mockAuthConnector
    }
  }

  "PrepareAccountController" should {

    "use the correct AuthConnector" in {
      PrepareAccountController.auth shouldBe AuthConnector
    }

    "use the correct ResourceConn" in {
      PrepareAccountController.resourceConn shouldBe Repositories.cTRepository
    }

    "use the correct PrepareAccountService" in {
      PrepareAccountController.service shouldBe PrepareAccountService
    }
  }

  "updateCompanyEndDate" should {

    val rID = "testRegID"
    val oID = "testOID"

    lazy val validAuthority = Authority("test.url", oID, "testGatewayId", "test.userDetailsLink")

    val prepareAccountModel = PrepareAccountModel("HMRCEndDate", Some("2010"), Some("12"), Some("12"))

    "return a 200 http response and a PrepareAccountModel as json" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.eq(rID))).thenReturn(Future.successful(Some(rID -> oID)))

      when(mockPrepareAccountService.updateEndDate(Matchers.eq(rID), Matchers.eq(prepareAccountModel)))
        .thenReturn(Future.successful(Some(prepareAccountModel)))

      val result = call(prepareAccountController.updateCompanyEndDate(rID), FakeRequest().withBody[JsValue](Json.toJson(prepareAccountModel)))
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(prepareAccountModel)
    }

    "return a 404 http response if the users' corporation tax record does not exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.eq(rID))).thenReturn(Future.successful(Some(rID -> oID)))

      when(mockPrepareAccountService.updateEndDate(Matchers.eq(rID), Matchers.eq(prepareAccountModel)))
        .thenReturn(Future.successful(None))

      val result = call(prepareAccountController.updateCompanyEndDate(rID), FakeRequest().withBody[JsValue](Json.toJson(prepareAccountModel)))
      status(result) shouldBe NOT_FOUND
    }

    "return a 404 http response if the user does not have an identifiable record" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.eq(rID))).thenReturn(Future.successful(None))

      when(mockPrepareAccountService.updateEndDate(Matchers.eq(rID), Matchers.eq(prepareAccountModel)))
        .thenReturn(Future.successful(None))

      val result = call(prepareAccountController.updateCompanyEndDate(rID), FakeRequest().withBody[JsValue](Json.toJson(prepareAccountModel)))
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 http response if the user is trying to access another users' record" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.eq(rID))).thenReturn(Future.successful(Some("differentRID" -> "differentOID")))

      when(mockPrepareAccountService.updateEndDate(Matchers.eq(rID), Matchers.eq(prepareAccountModel)))
        .thenReturn(Future.successful(None))

      val result = call(prepareAccountController.updateCompanyEndDate(rID), FakeRequest().withBody[JsValue](Json.toJson(prepareAccountModel)))
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 http response if the user does not have an authority" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      when(mockCTDataRepository.getOid(Matchers.eq(rID))).thenReturn(Future.successful(Some(rID -> oID)))

      when(mockPrepareAccountService.updateEndDate(Matchers.eq(rID), Matchers.eq(prepareAccountModel)))
        .thenReturn(Future.successful(None))

      val result = call(prepareAccountController.updateCompanyEndDate(rID), FakeRequest().withBody[JsValue](Json.toJson(prepareAccountModel)))
      status(result) shouldBe FORBIDDEN
    }
  }
}
