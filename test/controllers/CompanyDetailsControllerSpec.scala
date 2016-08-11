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

import auth.AuthorisationResource
import connectors.AuthConnector
import fixtures.{CompanyDetailsFixture, AuthFixture}
import helpers.SCRSSpec
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, OK, FORBIDDEN, NOT_FOUND}
import services.CompanyDetailsService

import scala.concurrent.Future

class CompanyDetailsControllerSpec extends SCRSSpec with AuthFixture with CompanyDetailsFixture {

  trait Setup {
    val controller = new CompanyDetailsController {
      override val auth = mockAuthConnector
      override val resourceConn = mockCTDataRepository
      override val companyDetailsService = mockCompanyDetailsService
    }
  }

  val registrationID = "12345"

  "CompanyDetailsController" should {
    "use the correct AuthConnector" in {
      CompanyDetailsController.auth shouldBe AuthConnector
    }
    "use the correct CompanyDetailsService" in {
      CompanyDetailsController.companyDetailsService shouldBe CompanyDetailsService
    }
  }

  "retrieveCompanyDetails" should {
    "return a 200 - Ok and a Company details record if one is found in the database" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(Some("testRegID", "testOID")))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetailsResponse))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(validCompanyDetailsResponse)
    }

    "return a 404 - Not Found if the record does not exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(Some("testRegID", "testOID")))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, None)

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe Json.parse(s"""{"statusCode":"404","message":"Could not find company details record"}""")
    }

    "return a 403 - Forbidden if the user cannot be authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(None))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }
  }

  "updateCompanyDetails" should {
    "return a 200 - Ok and a company details response if a record is updated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, Some(validCompanyDetailsResponse))

      val request = FakeRequest().withBody(Json.toJson(validCompanyDetailsResponse))
      val result = call(controller.updateCompanyDetails(registrationID), request)
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(validCompanyDetailsResponse)
    }

    "return a 404 - Not Found if the recorde to update does not exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, None)

      val request = FakeRequest().withBody(Json.toJson(validCompanyDetailsResponse))
      val result = call(controller.updateCompanyDetails(registrationID), request)
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe Json.parse(s"""{"statusCode":"404","message":"Could not find company details record"}""")
    }

    "return a 403 - Forbidden if the user cannot be authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)

      val request = FakeRequest().withBody(Json.toJson(validCompanyDetailsResponse))
      val result = call(controller.updateCompanyDetails(registrationID), request)
      status(result) shouldBe FORBIDDEN
    }
  }
}