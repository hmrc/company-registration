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

package services

import fixtures.ContactDetailsFixture
import helpers.SCRSSpec
import mocks.SCRSMocks
import org.scalatest.mockito.MockitoSugar
import repositories.Repositories
import uk.gov.hmrc.play.test.UnitSpec

class ContactDetailsServiceSpec extends UnitSpec with SCRSMocks with MockitoSugar with ContactDetailsFixture {

  trait Setup {
    val service = new ContactDetailsService {
      override val corporationTaxRegistrationRepository = mockCTDataRepository
    }
  }


  "retrieveContactDetails" should {
    "return a contact details response if a contact details record exists" in new Setup {
      CTDataRepositoryMocks.retrieveContactDetails(Some(contactDetails))

      await(service.retrieveContactDetails("12345")) shouldBe Some(contactDetails)
    }

    "returns None if a contact details record is not found" in new Setup {
      CTDataRepositoryMocks.retrieveContactDetails(None)

      await(service.retrieveContactDetails("12345")) shouldBe None
    }
  }

  "updateContactDetails" should {
    "return a contact details response if a contact details record exists" in new Setup {
      CTDataRepositoryMocks.updateContactDetails(Some(contactDetails))

      await(service.updateContactDetails("12345", contactDetails)) shouldBe Some(contactDetails)
    }

    "returns None if a contact details record is not found" in new Setup {
      CTDataRepositoryMocks.updateContactDetails(None)

      await(service.updateContactDetails("12345", contactDetails)) shouldBe None
    }
  }
}
