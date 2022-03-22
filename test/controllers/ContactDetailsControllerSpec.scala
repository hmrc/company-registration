/*
 * Copyright 2022 HM Revenue & Customs
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

import fixtures.ContactDetailsFixture
import helpers.BaseSpec
import mocks.{AuthorisationMocks, MockMetricsService}
import models.ErrorResponse
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationMongoRepository, MissingCTDocument, Repositories}
import services.PrepareAccountService
import uk.gov.hmrc.auth.core.MissingBearerToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactDetailsControllerSpec extends BaseSpec with AuthorisationMocks with ContactDetailsFixture {
  val mockPrepareAccountService = mock[PrepareAccountService]
  val mockRepositories = mock[Repositories]
  override val mockResource: CorporationTaxRegistrationMongoRepository = mockTypedResource[CorporationTaxRegistrationMongoRepository]

  trait Setup {
    val controller = new ContactDetailsController(
      MockMetricsService,
      mockContactDetailsService,
      mockAuthConnector,
      mockRepositories,
      stubControllerComponents()
    ) {
      override lazy val resource: CorporationTaxRegistrationMongoRepository = mockResource
    }
  }

  val registrationID = "reg-12345"
  val internalId = "int-12345"
  val otherInternalID = "other-int-12345"

  val contactDetailsJsonResponse = Json.toJson(contactDetailsResponse(registrationID))

  "retrieveContactDetails" should {

    "return a 200 with contact details in the json body when authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      ContactDetailsServiceMocks.retrieveContactDetails(registrationID, Some(contactDetails))

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe OK
      contentAsJson(result) shouldBe contactDetailsJsonResponse
    }

    "return a 404 when the user is authorised but contact details cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      ContactDetailsServiceMocks.retrieveContactDetails(registrationID, None)

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
      contentAsJson(result) shouldBe ErrorResponse.contactDetailsNotFound
    }

    "return a 404 when the CT document cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.failed(new MissingCTDocument("testRegId")))

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(otherInternalID))

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 401 when the user is not logged in" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe UNAUTHORIZED
    }
  }

  "updateContactDetails" should {

    val request = FakeRequest().withBody(Json.toJson(contactDetails))

    "return a 200 with contact details in the json body when authorised" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      ContactDetailsServiceMocks.updateContactDetails(registrationID, Some(contactDetails))

      val result = controller.updateContactDetails(registrationID)(request)
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(contactDetailsJsonResponse)
    }

    "return a 404 when the user is authorised but contact details cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(internalId))

      ContactDetailsServiceMocks.updateContactDetails(registrationID, None)

      val result = controller.updateContactDetails(registrationID)(request)
      status(result) shouldBe NOT_FOUND
    }

    "return a 404 when the CT document cannot be found" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.failed(new MissingCTDocument("testRegId")))

      val result = controller.updateContactDetails(registrationID)(request)
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      mockAuthorise(Future.successful(internalId))
      mockGetInternalId(Future.successful(otherInternalID))

      val result = controller.updateContactDetails(registrationID)(request)
      status(result) shouldBe FORBIDDEN
    }

    "return a 401 when the user is not logged in" in new Setup {
      mockAuthorise(Future.failed(MissingBearerToken()))

      val result = controller.updateContactDetails(registrationID)(request)
      status(result) shouldBe UNAUTHORIZED
    }
  }
}
