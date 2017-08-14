/*
 * Copyright 2017 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import connectors.AuthConnector
import fixtures.{AuthFixture, CompanyDetailsFixture}
import helpers.SCRSSpec
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{FORBIDDEN, NOT_FOUND, OK, call}
import services.{CompanyDetailsService, MetricsService}
import mocks.{MockMetricsService, SCRSMocks}
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class CompanyDetailsControllerSpec extends UnitSpec with MockitoSugar with SCRSMocks with AuthFixture with CompanyDetailsFixture {

  implicit val system = ActorSystem("CR")
  implicit val materializer = ActorMaterializer()

  trait Setup {
    val controller = new CompanyDetailsController {
      override val auth = mockAuthConnector
      override val resourceConn = mockCTDataRepository
      override val companyDetailsService = mockCompanyDetailsService
      override val metricsService = MockMetricsService
    }
  }

  val registrationID = "12345"


  "retrieveCompanyDetails" should {
    "return a 200 - Ok and a Company details record if one is found in the database" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.any()))
        .thenReturn(Future.successful(Some(registrationID -> validAuthority.ids.internalId)))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetails))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(validCompanyDetailsResponse)
    }

    "return a 404 - Not Found if the record does not exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.any()))
        .thenReturn(Future.successful(Some(registrationID -> validAuthority.ids.internalId)))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, None)

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe Json.parse(s"""{"statusCode":"404","message":"Could not find company details record"}""")
    }

    "return a 403 - Forbidden if the user cannot be authenticated" in new Setup {
      val authority = validAuthority.copy(ids = validAuthority.ids.copy(internalId = "notAuthorisedID"))
      AuthenticationMocks.getCurrentAuthority(Some(authority))
      AuthorisationMocks.getInternalId("testID", Some("testRegID" -> "testID"))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 - Forbidden if the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getInternalId("testID", Some("testRegID" -> "testID"))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 404 - Not found when an authority is found but nothing is returned from" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.any())).thenReturn(Future.successful(None))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }
  }

  "updateCompanyDetails" should {
    "return a 200 - Ok and a company details response if a record is updated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.any()))
        .thenReturn(Future.successful(Some(registrationID -> validAuthority.ids.internalId)))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetails))

      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, Some(validCompanyDetails))

      val request = FakeRequest().withBody(Json.toJson(validCompanyDetails))
      val result = call(controller.updateCompanyDetails(registrationID), request)
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(validCompanyDetailsResponse)
    }

    "return a 404 - Not Found if the recorde to update does not exist" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

      when(mockCTDataRepository.getInternalId(Matchers.any()))
        .thenReturn(Future.successful(Some(registrationID -> validAuthority.ids.internalId)))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetails))

      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, None)

      val request = FakeRequest().withBody(Json.toJson(validCompanyDetailsResponse))
      val result = call(controller.updateCompanyDetails(registrationID), request)
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe Json.parse(s"""{"statusCode":"404","message":"Could not find company details record"}""")
    }

    "return a 403 - Forbidden if the user cannot be authenticated" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)

      when(mockCTDataRepository.getInternalId(Matchers.any())).thenReturn(Future.successful(Some("testRegID" -> "testID")))
      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetails))

      val request = FakeRequest().withBody(Json.toJson(validCompanyDetailsResponse))
      val result = call(controller.updateCompanyDetails(registrationID), request)
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      when(mockCTDataRepository.getInternalId(Matchers.anyString()))
        .thenReturn(Future.successful(Some("testRegID" -> (validAuthority.ids.internalId + "123"))))

      val request = FakeRequest().withBody(Json.toJson(validCompanyDetailsResponse))
      val result = call(controller.updateCompanyDetails(registrationID), request)
      status(result) shouldBe FORBIDDEN
    }
  }
}
