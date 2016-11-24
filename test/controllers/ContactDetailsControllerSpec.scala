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
import fixtures.{AuthFixture, ContactDetailsFixture,ContactDetailsResponse}
import helpers.SCRSSpec
import models.ErrorResponse
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CorporationTaxRegistrationService, ContactDetailsService}

class ContactDetailsControllerSpec extends SCRSSpec with ContactDetailsFixture with AuthFixture {

  trait Setup {
    val controller = new ContactDetailsController {
      override val contactDetailsService = mockContactDetailsService
      override val resourceConn = mockCTDataRepository
      override val auth = mockAuthConnector
    }
  }

  val registrationID = "12345"

  "ContactDetailsController" should {
    "use the correct auth connector" in {
      ContactDetailsController.auth shouldBe AuthConnector
    }
    "use the correct resource connector" in {
      ContactDetailsController.resourceConn shouldBe CorporationTaxRegistrationService.corporationTaxRegistrationRepository
    }
    "use the correct service" in {
      ContactDetailsController.contactDetailsService shouldBe ContactDetailsService
    }
  }

  "retrieveContactDetails" should {
    "return a 200 with contact details in the json body when authorised" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId("testID", Some(registrationID -> validAuthority.ids.internalId))
      ContactDetailsServiceMocks.retrieveContactDetails(registrationID, Some(contactDetails))

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(contactDetailsResponse)
    }

    "return a 404 when the user is authorised but contact details cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId("testID", Some(registrationID -> validAuthority.ids.internalId))
      ContactDetailsServiceMocks.retrieveContactDetails(registrationID, None)

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
      await(jsonBodyOf(result)) shouldBe ErrorResponse.contactDetailsNotFound
    }

    "return a 404 when the auth resource cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId("testID", None)
      ContactDetailsServiceMocks.retrieveContactDetails(registrationID, None)

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      val authority = validAuthority.copy(ids = validAuthority.ids.copy(internalId = "notAuthorisedID"))
      AuthenticationMocks.getCurrentAuthority(Some(authority))
      AuthorisationMocks.getInternalId("testID", Some("testRegID" -> "testID"))

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getInternalId("testID", Some("testRegID" -> "testID"))

      val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
      status(result) shouldBe FORBIDDEN
    }
  }

  "updateContactDetails" should {
    "return a 200 with contact details in the json body when authorised" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId("testID", Some(registrationID -> validAuthority.ids.internalId))
      ContactDetailsServiceMocks.updateContactDetails(registrationID, Some(contactDetails))

      val response = FakeRequest().withBody(Json.toJson(contactDetails))

      val result = call(controller.updateContactDetails(registrationID), response)
      status(result) shouldBe OK
      await(jsonBodyOf(result)) shouldBe Json.toJson(contactDetailsResponse)
    }

    "return a 404 when the user is authorised but contact details cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId("testID", Some(registrationID -> validAuthority.ids.internalId))
      ContactDetailsServiceMocks.updateContactDetails(registrationID, None)

      val response = FakeRequest().withBody(Json.toJson(contactDetails))

      val result = call(controller.updateContactDetails(registrationID), response)
      status(result) shouldBe NOT_FOUND
    }

    "return a 400 when the auth resource cannot be found" in new Setup {
      AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
      AuthorisationMocks.getInternalId("testID", None)

      val response = FakeRequest().withBody(Json.toJson(contactDetails))

      val result = call(controller.updateContactDetails(registrationID), response)
      status(result) shouldBe NOT_FOUND
    }

    "return a 403 when the user is unauthorised to access the record" in new Setup {
      val authority = validAuthority.copy(ids = validAuthority.ids.copy(internalId = "notAuthorisedID"))
      AuthenticationMocks.getCurrentAuthority(Some(authority))
      AuthorisationMocks.getInternalId("testID", Some("testRegID" -> "testID"))

      val response = FakeRequest().withBody(Json.toJson(contactDetails))

      val result = call(controller.updateContactDetails(registrationID), response)
      status(result) shouldBe FORBIDDEN
    }

    "return a 403 when the user is not logged in" in new Setup {
      AuthenticationMocks.getCurrentAuthority(None)
      AuthorisationMocks.getInternalId("testID", Some("testRegID" -> "testID"))

      val response = FakeRequest().withBody(Json.toJson(contactDetails))

      val result = call(controller.updateContactDetails(registrationID), response)
      status(result) shouldBe FORBIDDEN
    }
  }
}
