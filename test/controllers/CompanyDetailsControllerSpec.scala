/*
 * Copyright 2019 HM Revenue & Customs
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

import fixtures.CompanyDetailsFixture
import helpers.BaseSpec
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import mocks.{AuthorisationMocks, MockMetricsService}
import models.ErrorResponse
import repositories.MissingCTDocument
import uk.gov.hmrc.auth.core.MissingBearerToken

import scala.concurrent.Future

class CompanyDetailsControllerSpec extends BaseSpec with AuthorisationMocks with CompanyDetailsFixture {

  trait Setup {
    val controller = new CompanyDetailsController {
      override val authConnector = mockAuthConnector
      override val resource = mockResource
      override val companyDetailsService = mockCompanyDetailsService
      override val metricsService = MockMetricsService
    }
  }

  val registrationID = "reg-12345"
  val internalId = "int-12345"
  val otherInternalID = "other-int-12345"

  val companyDetailsResponseJson = Json.toJson(Some(validCompanyDetailsResponse(registrationID)))

  "retrieveCompanyDetails" should {

    "return a 200 and a Company details record when authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, Some(validCompanyDetails))

      val result = await(controller.retrieveCompanyDetails(registrationID)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe companyDetailsResponseJson

    }

    "return a 404 if company details are not found on the CT document" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.retrieveCompanyDetails(registrationID, None)

      val result = await(controller.retrieveCompanyDetails(registrationID)(FakeRequest()))
      status(result) shouldBe NOT_FOUND
      contentAsJson(result) shouldBe ErrorResponse.companyDetailsNotFound
    }

    "return a 404 when the CT document cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.failed(new MissingCTDocument("hfbhdbf")))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(otherInternalID))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 401 when the user is not logged in" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result = controller.retrieveCompanyDetails(registrationID)(FakeRequest())
      status(result) shouldBe UNAUTHORIZED
    }
  }

  "updateCompanyDetails" should {

    val request = FakeRequest().withBody(Json.toJson(validCompanyDetails))

    "return a 200 and a company details response if the user is authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, Some(validCompanyDetails))

      val result = await(controller.updateCompanyDetails(registrationID)(request))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe companyDetailsResponseJson
    }

    "return a 404 if the record to update does not exist" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, None)

      val result = await(controller.updateCompanyDetails(registrationID)(request))
      status(result) shouldBe NOT_FOUND
      contentAsJson(result) shouldBe ErrorResponse.companyDetailsNotFound
    }

    "return a 404 when the user is authorised but the CT document does not exist" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.failed(new MissingCTDocument("hfbhdbf")))

      val result = await(controller.updateCompanyDetails(registrationID)(request))
      status(result) shouldBe NOT_FOUND
    }

    "return a 401 when the user is not logged in" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result = await(controller.updateCompanyDetails(registrationID)(request))
      status(result) shouldBe UNAUTHORIZED
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(otherInternalID))

      val result = await(controller.updateCompanyDetails(registrationID)(request))
      status(result) shouldBe FORBIDDEN
    }

    "verify that metrics are captured on a successful update" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      CompanyDetailsServiceMocks.updateCompanyDetails(registrationID, Some(validCompanyDetails))

      val result = await(controller.updateCompanyDetails(registrationID)(request))
      status(result) shouldBe OK
    }
  }
}
