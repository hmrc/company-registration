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

package services

import fixtures.{CorporationTaxRegistrationFixture, MongoFixture}
import helpers.SCRSSpec
import play.api.test.Helpers._
import repositories.{CorporationTaxRegistrationRepository, Repositories}

class CorporationTaxRegistrationServiceSpec extends SCRSSpec with CorporationTaxRegistrationFixture with MongoFixture{

	implicit val mongo = mongoDB

	class Setup {
		val service = new CorporationTaxRegistrationService {
			override val CorporationTaxRegistrationRepository: CorporationTaxRegistrationRepository = mockCTDataRepository
			override val sequenceRepository = mockSequenceRepository
		}
	}

	"CorporationTaxRegistrationService" should {
		"use the correct CorporationTaxRegistrationRepository" in {
			CorporationTaxRegistrationService.CorporationTaxRegistrationRepository shouldBe Repositories.cTRepository
		}
	}

	"createCorporationTaxRegistrationRecord" should {
		"create a new ctData record and return a 201 - Created response" in new Setup {
			CTDataRepositoryMocks.createCorporationTaxRegistration(validCorporationTaxRegistration)

			val result = service.createCorporationTaxRegistrationRecord("54321", "12345", "en")
			await(result) shouldBe validCorporationTaxRegistrationResponse
		}
	}

	"retrieveMetadataRecord" should {
		"return MetadataResponse Json and a 200 - Ok when a metadata record is retrieved" in new Setup {
			CTDataRepositoryMocks.retrieveCorporationTaxRegistration(Some(validCorporationTaxRegistration))

			val result = service.retrieveCorporationTaxRegistrationRecord("testRegID")
			await(result) shouldBe Some(validCorporationTaxRegistrationResponse)
		}

		"return a 404 - Not found when no record is retrieved" in new Setup {
			CTDataRepositoryMocks.retrieveCorporationTaxRegistration(None)

			val result = service.retrieveCorporationTaxRegistrationRecord("testRegID")
			await(result) shouldBe None
		}
	}
}
