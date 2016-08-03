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
import models.{CorporationTaxRegistration, Language}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.CorporationTaxRegistrationService
import play.api.mvc.Results.{Created, Ok}
import play.api.test.Helpers._

class CorporationTaxRegistrationControllerSpec extends SCRSSpec with CorporationTaxRegistrationFixture with AuthFixture {

	val validLanguage = Json.toJson(Language("en"))

	class Setup {
		val controller = new CorporationTaxRegistrationController {
			override val ctService: CorporationTaxRegistrationService = mockCTDataService
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
			CTServiceMocks.createCTDataRecord(Created(Json.toJson(validCTData)))
			AuthenticationMocks.getCurrentAuthority(Some(validAuthority))

			val request = FakeRequest().withJsonBody(validLanguage)
			val result = call(controller.createCorporationTaxRegistration("0123456789"), request)
			await(jsonBodyOf(result)).as[CorporationTaxRegistration] shouldBe validCTData
			status(result) shouldBe CREATED
		}

		"return a 403 - forbidden when the user is not authenticated" in new Setup {
			AuthenticationMocks.getCurrentAuthority(None)

			val request = FakeRequest().withJsonBody(validCTDataJson)
			val result = call(controller.createCorporationTaxRegistration("0123456789"), request)
			status(result) shouldBe FORBIDDEN
		}
	}
}
