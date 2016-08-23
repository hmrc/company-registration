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
import fixtures.{AuthFixture, CorporationTaxRegistrationFixture}
import helpers.SCRSSpec
import models.{CorporationTaxRegistration, CorporationTaxRegistrationResponse}
import org.mockito.Matchers
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.CorporationTaxRegistrationService
import play.api.test.Helpers._

import scala.concurrent.Future
import org.mockito.Mockito._

class CorporationTaxRegistrationControllerSpec extends SCRSSpec with CorporationTaxRegistrationFixture with AuthFixture {

	class Setup {
		val controller = new CorporationTaxRegistrationController {
			override val ctService: CorporationTaxRegistrationService = mockCTDataService
			override val resourceConn = mockCTDataRepository
			override val auth: AuthConnector = mockAuthConnector
		}
	}

	"CorporationTaxRegistrationController" should {
		"use the correct CTDataService" in {
			CorporationTaxRegistrationController.ctService shouldBe CorporationTaxRegistrationService
		}
		"use the correct authconnector" in {
			CorporationTaxRegistrationController.auth shouldBe AuthConnector
		}
	}

	"createCorporationTaxRegistration" should {
		"return a 201 when a new entry is created from the parsed json" in new Setup {
			CTServiceMocks.createCTDataRecord(validCorporationTaxRegistrationResponse)
			AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

			val request = FakeRequest().withJsonBody(Json.toJson(validCorporationTaxRegistrationRequest))
			val result = call(controller.createCorporationTaxRegistration("0123456789"), request)
			await(jsonBodyOf(result)).as[CorporationTaxRegistrationResponse] shouldBe validCorporationTaxRegistrationResponse
			status(result) shouldBe CREATED
		}

		"return a 403 - forbidden when the user is not authenticated" in new Setup {
			AuthenticationMocks.getCurrentAuthority(None)

			val request = FakeRequest().withJsonBody(Json.toJson(validCorporationTaxRegistration))
			val result = call(controller.createCorporationTaxRegistration("0123456789"), request)
			status(result) shouldBe FORBIDDEN
		}
	}

	"retrieveMetadata" should {
		"return a 200 and a metadata model is one is found" in new Setup {
			val regId = "testRegId"
			CTServiceMocks.retrieveCTDataRecord(regId, Some(validCorporationTaxRegistrationResponse))
			AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

			when(mockCTDataRepository.getOid(Matchers.eq(regId))).
				thenReturn(Future.successful(Some((regId,validAuthority.oid))))

			val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())
			status(result) shouldBe OK
			await(jsonBodyOf(result)).asOpt[CorporationTaxRegistrationResponse] shouldBe Some(validCorporationTaxRegistrationResponse)
		}

		"return a 403 - forbidden when the user is not authenticated" in new Setup {
			val regId = "testRegId"
			AuthenticationMocks.getCurrentAuthority(None)

			when(mockCTDataRepository.getOid(Matchers.any())).
				thenReturn(Future.successful(None))

			val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())
			status(result) shouldBe FORBIDDEN
		}

		"return a 403 - forbidden when the user is logged in but not authorised to access the resource" in new Setup {
			val regId = "testRegId"
			AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
			when(mockCTDataRepository.getOid(Matchers.eq(regId))).
				thenReturn(Future.successful(Some((regId, validAuthority.oid + "xxx"))))

			val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())
			status(result) shouldBe FORBIDDEN
		}

		"return a 404 - not found logged in the requested document doesn't exist" in new Setup {
			val regId = "testRegId"
			AuthenticationMocks.getCurrentAuthority(Some(validAuthority))
			when(mockCTDataRepository.getOid(Matchers.eq(regId))).thenReturn(Future.successful(None))

			val result = call(controller.retrieveCorporationTaxRegistration(regId), FakeRequest())
			status(result) shouldBe NOT_FOUND
		}
	}
}
