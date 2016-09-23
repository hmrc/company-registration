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
import fixtures.{AccountingDetailsFixture, AuthFixture}
import helpers.SCRSSpec
import org.mockito.Mockito._
import models.{AccountingDetailsResponse, ErrorResponse}
import org.mockito.Matchers
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AccountingDetailsService, CorporationTaxRegistrationService}

import scala.concurrent.Future

class AccountingDetailsControllerSpec extends SCRSSpec with AuthFixture with AccountingDetailsFixture {

  trait Setup {
    val controller = new AccountingDetailsController {
      override val auth = mockAuthConnector
      override val resourceConn = mockCTDataRepository
      override val accountingDetailsService = mockAccountingDetailsService
    }
  }


  val registrationID = "12345"

  "AccountingDetailsController" should {
    "use the correct auth connector" in {
      AccountingDetailsController.auth shouldBe AuthConnector
    }
    "use the correct resource connector" in {
      AccountingDetailsController.resourceConn shouldBe CorporationTaxRegistrationService.CorporationTaxRegistrationRepository
    }
    "use the correct service" in {
      AccountingDetailsController.accountingDetailsService shouldBe AccountingDetailsService
    }
  }

  "retrieveAccountingDetails" should {
    "return a 200 with accounting details in the json body when authorised" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> "testOID")))
      AccountingDetailsServiceMocks.retrieveAccountingDetails(registrationID, Some(validAccountingDetailsResponse))

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe OK
      jsonBodyOf(result).as[AccountingDetailsResponse] shouldBe validAccountingDetailsResponse
    }


    "return a 404 when the user is authorised but accounting details cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> "testOID")))
      AccountingDetailsServiceMocks.retrieveAccountingDetails(registrationID, None)

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe ErrorResponse.accountingDetailsNotFound
    }

    "return a 404 when the auth resource cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.anyString()))
        .thenReturn(Future.successful(None))
      AccountingDetailsServiceMocks.retrieveAccountingDetails(registrationID, None)

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }


    "return a 403 when the user is unauthorised to access the record" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority.copy(oid = "notAuthorisedOID")))
      when(mockCTDataRepository.getOid(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> "testOID")))

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      when(mockCTDataRepository.getOid(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> "testOID")))

      val result = controller.retrieveAccountingDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }
  }

  "updateAccountingDetails" should {
    "return a 200 with accounting details in the json body when authorised" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> "testOID")))
      AccountingDetailsServiceMocks.updateAccountingDetails(registrationID, Some(validAccountingDetailsResponse))

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe OK
      jsonBodyOf(result).as[AccountingDetailsResponse] shouldBe validAccountingDetailsResponse
    }
  }

    "return a 404 when the user is authorised but accounting details cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getOID("testOID", Some("testRegID" -> "testOID"))
      AccountingDetailsServiceMocks.updateAccountingDetails(registrationID, None)

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe NOT_FOUND
    }

    "return a 400 when the auth resource cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getOID("testOID", None)

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> (validAuthority.oid + "123"))))

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
//      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getOID("testOID", Some("testRegID" -> "testOID"))

      val response = FakeRequest().withBody(Json.toJson(validAccountingDetails))

      val result = call(controller.updateAccountingDetails(registrationID), response)
      status(result) shouldBe FORBIDDEN
    }
}
