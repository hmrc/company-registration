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

import java.util.UUID

import connectors.AuthConnector
import fixtures.{AuthFixture, CompanyDetailsFixture}
import helpers.SCRSSpec
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{FORBIDDEN, NOT_FOUND, OK, BAD_REQUEST, call}
import services.HandoffCHDataService

import scala.concurrent.Future

class HandoffCHDataControllerSpec extends SCRSSpec with AuthFixture with CompanyDetailsFixture {

  trait Setup {
    val controller = new HandoffCHDataController {
      override val auth = mockAuthConnector
      override val resourceConn = mockCTDataRepository
      override val handoffService = mockHandoffService
    }
  }

  val registrationID = UUID.randomUUID().toString

  "HandoffCHDataController" should {
    "use the correct AuthConnector" in {
      HandoffCHDataController.auth shouldBe AuthConnector
    }
    "use the correct CompanyDetailsService" in {
      HandoffCHDataController.handoffService shouldBe HandoffCHDataService
    }
  }

  "retrieve handoff CH data" should {
    "return a 200 - Ok and the CH Handoff data if it's found in the database" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(Some("testRegID" -> "testOID")))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetailsResponse))
      val json: String = """{"a":1}"""
      when(mockHandoffService.retrieveHandoffCHData(Matchers.eq(registrationID)))
          .thenReturn(Future.successful(Some(Json.parse(json))))

      val result = controller.retrieveHandoffCHData(registrationID)(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.parse(json)
    }

    "return a 404 - Not Found if the record does not exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(Some("testRegID" -> "testOID")))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, None)

      when(mockHandoffService.retrieveHandoffCHData(Matchers.eq(registrationID)))
        .thenReturn(Future.successful(None))


      val result = controller.retrieveHandoffCHData(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe Json.parse(s"""{"statusCode":"404","message":"Could not find CH handoff data"}""")
    }

    "return a 403 - Forbidden if the user cannot be authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority.copy(oid = "notAuthorisedOID")))
      AuthorisationMocks.getOID("testOID", Some("testRegID" -> "testOID"))

      val result = controller.retrieveHandoffCHData(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 - Forbidden if the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getOID("testOID", Some("testRegID" -> "testOID"))

      val result = controller.retrieveHandoffCHData(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 - Not found when an authority is found but nothing is returned from" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(None))

      val result = controller.retrieveHandoffCHData(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }

  "store handoff CH data" should {

    "return a 200 - Ok and store the CH Handoff data" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      val json = """{"a":1}"""

      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(Some("testRegID" -> "testOID")))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetailsResponse))

      when(mockHandoffService.storeHandoffCHData(Matchers.eq(registrationID), Matchers.any[JsValue]))
        .thenReturn(Future.successful(true))

      val request = FakeRequest().withBody(Json.parse(json))
      val result = call(controller.storeHandoffCHData(registrationID), request)

      status(result) shouldBe OK

      val captor = ArgumentCaptor.forClass(classOf[JsValue])

      verify(mockHandoffService).storeHandoffCHData(Matchers.eq(registrationID), captor.capture())

      captor.getValue shouldBe Json.parse(json)
    }

    "return a 400 - if the data wasn't stored" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(Some("testRegID" -> "testOID")))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetailsResponse))

      val json: String = """{"a":1}"""
      when(mockHandoffService.storeHandoffCHData(Matchers.eq(registrationID), Matchers.any[JsValue]))
        .thenReturn(Future.successful(false))

      val request = FakeRequest().withBody(Json.parse(json))
      val result = call( controller.storeHandoffCHData(registrationID), request)

      status(result) shouldBe BAD_REQUEST
      await(jsonBodyOf(result)) shouldBe Json.parse(s"""{"statusCode":"400","message":"Could not store the CH handoff data"}""")
    }

    "return a 403 - Forbidden if the user cannot be authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority.copy(oid = "notAuthorisedOID")))
      AuthorisationMocks.getOID("testOID", Some("testRegID" -> "testOID"))

      val request = FakeRequest().withBody(Json.parse("{}"))
      val result = call( controller.storeHandoffCHData(registrationID), request)

      status(result) shouldBe FORBIDDEN
    }

    "return a 403 - Forbidden if the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getOID("testOID", Some("testRegID" -> "testOID"))

      val request = FakeRequest().withBody(Json.parse("{}"))
      val result = call( controller.storeHandoffCHData(registrationID), request)

      status(result) shouldBe FORBIDDEN
    }

    "return a 404 - Not found when an authority is found but nothing is returned from" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getOid(Matchers.any())).thenReturn(Future.successful(None))

      val request = FakeRequest().withBody(Json.parse("{}"))
      val result = call( controller.storeHandoffCHData(registrationID), request)

      status(result) shouldBe NOT_FOUND
    }
  }

}
