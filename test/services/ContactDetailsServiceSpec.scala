/*
 * Copyright 2024 HM Revenue & Customs
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

import fixtures.ContactDetailsFixture
import helpers.BaseSpec
import play.api.test.Helpers._
import repositories.CorporationTaxRegistrationMongoRepository

class ContactDetailsServiceSpec extends BaseSpec with ContactDetailsFixture {

  trait Setup {
    val service: ContactDetailsService = new ContactDetailsService {
      override val corporationTaxRegistrationMongoRepository: CorporationTaxRegistrationMongoRepository = mockCTDataRepository
    }
  }


  "retrieveContactDetails" must {
    "return a contact details response if a contact details record exists" in new Setup {
      CTDataRepositoryMocks.retrieveContactDetails(Some(contactDetails))

      await(service.retrieveContactDetails("12345")) mustBe Some(contactDetails)
    }

    "returns None if a contact details record is not found" in new Setup {
      CTDataRepositoryMocks.retrieveContactDetails(None)

      await(service.retrieveContactDetails("12345")) mustBe None
    }
  }

  "updateContactDetails" must {
    "return a contact details response if a contact details record exists" in new Setup {
      CTDataRepositoryMocks.updateContactDetails(Some(contactDetails))

      await(service.updateContactDetails("12345", contactDetails)) mustBe Some(contactDetails)
    }

    "returns None if a contact details record is not found" in new Setup {
      CTDataRepositoryMocks.updateContactDetails(None)

      await(service.updateContactDetails("12345", contactDetails)) mustBe None
    }
  }
}
