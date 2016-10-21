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

import fixtures.AccountingDetailsFixture
import helpers.SCRSSpec

class AccountingDetailsServiceSpec extends SCRSSpec with AccountingDetailsFixture {

  trait Setup {
    val service = new AccountingDetailsService {
      override val corporationTaxRegistrationRepository = mockCTDataRepository
    }
  }

  val registrationID = "12345"

  "retrieveAccountingDetails" should {
    "return a AccountingDetailsResponse when a company details record is found" in new Setup {
      CTDataRepositoryMocks.retrieveAccountingDetails(Some(validAccountingDetails))

      await(service.retrieveAccountingDetails(registrationID)) shouldBe Some(validAccountingDetails)
    }

    "return a None when the record to retrieve is not found in the repository" in new Setup {
      CTDataRepositoryMocks.retrieveAccountingDetails(None)

      await(service.retrieveAccountingDetails(registrationID)) shouldBe None
    }
  }

  "updateAccountingDetails" should {
    "return an AccountingDetailsResponse when a accounting details record is updated" in new Setup {
      CTDataRepositoryMocks.updateAccountingDetails(Some(validAccountingDetails))

      await(service.updateAccountingDetails(registrationID, validAccountingDetails)) shouldBe Some(validAccountingDetails)
    }

    "return a None when the record to update is not found in the repository" in new Setup {
      CTDataRepositoryMocks.updateAccountingDetails(None)

      await(service.updateAccountingDetails(registrationID, validAccountingDetails)) shouldBe None
    }
  }
}
